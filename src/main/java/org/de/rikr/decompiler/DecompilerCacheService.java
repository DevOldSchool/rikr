package org.de.rikr.decompiler;

import org.de.rikr.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caches decompiled sources per {@link ClassNode} instance, so classes with the same name in different JARs
 * never share a source. Every rename must call {@link #invalidate}; results computed before an
 * invalidation are discarded. Behind it, {@link SourceCache} keeps sources on disk by class bytes, between runs.
 */
public class DecompilerCacheService implements AutoCloseable {
    /** Where decompiled Java is kept between runs unless another folder is chosen. */
    public static final Path DEFAULT_DISK_CACHE_DIRECTORY = SourceCache.DEFAULT_DIRECTORY;
    /** How much decompiled Java is kept between runs unless another limit is chosen. */
    public static final long DEFAULT_DISK_CACHE_BYTES = SourceCache.DEFAULT_LIMIT_BYTES;

    // About 50 KB and 300 KB of class files: on a game client, 2 to 3 seconds of work that leaves the JIT about as
    // ready as after decompiling a large class.
    private static final int WARM_UP_MAX_CLASS_INSTRUCTIONS = 15_000;
    private static final int WARM_UP_INSTRUCTIONS = 90_000;

    // Classes are decompiled when they are opened, not a whole JAR ahead of time: on a game client that takes
    // minutes and gigabytes, and the first rename throws it all away.
    private final ExecutorService classExecutor;
    private final WorkerDecompiler worker;
    private final Logger logger;
    // Used and replaced only on the class executor's thread.
    private SourceCache diskCache;
    // ClassNode does not override equals/hashCode, so entries are keyed by instance.
    private final Map<ClassNode, OriginalLines> sources = new WeakHashMap<>();
    private long generation;

    public DecompilerCacheService(Logger logger) {
        this(new WorkerDecompiler(logger),
            SourceCache.in(SourceCache.DEFAULT_DIRECTORY, SourceCache.DEFAULT_LIMIT_BYTES, logger), logger);
    }

    /**
     * @param diskCache Java kept between runs, or null for none
     */
    DecompilerCacheService(WorkerDecompiler worker, SourceCache diskCache, Logger logger) {
        this.worker = worker;
        this.diskCache = diskCache;
        this.logger = logger;
        this.classExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rikr-decompiler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<DecompileResult> decompileClassAsync(ClassNode classNode) {
        if (classNode == null || classNode.name == null) {
            return CompletableFuture.completedFuture(DecompileResult.failure("Class node is missing."));
        }

        String cached = getDecompiledSource(classNode);
        if (cached != null && !cached.isEmpty()) {
            return CompletableFuture.completedFuture(DecompileResult.success(cached));
        }

        // Snapshot on the calling thread: renames mutate class nodes in place.
        String internalName = classNode.name;
        byte[] classBytes = toBytes(classNode);
        long startGeneration = currentGeneration();
        return CompletableFuture.supplyAsync(() -> {
            String stored = diskCache == null ? null : diskCache.read(classBytes);
            DecompileResult decompiled = stored != null
                ? DecompileResult.success(stored)
                : worker.decompile(internalName, classBytes);
            if (!decompiled.success()) {
                return decompiled;
            }
            if (stored == null && diskCache != null) {
                diskCache.write(classBytes, decompiled.source());
            }
            OriginalLines source = OriginalLines.parse(decompiled.source());
            cacheSource(classNode, source, startGeneration);
            return DecompileResult.success(source.source());
        }, classExecutor);
    }

    /**
     * Keeps decompiled Java in this folder from now on, and at most this much of it; what is in the folder used
     * before stays there. Takes effect after the class being decompiled, if any.
     *
     * @param directory the folder, or null for the default one
     */
    public void configureDiskCache(Path directory, long limitBytes) {
        Path chosen = directory == null ? SourceCache.DEFAULT_DIRECTORY : directory;
        classExecutor.execute(() -> {
            diskCache = SourceCache.in(chosen, limitBytes, logger);
            try {
                diskCache.trim();
            } catch (IOException | RuntimeException unreadable) {
                logger.log("[Decompiler] Can't check the size of " + diskCache.directory() + ": "
                    + unreadable.getMessage());
            }
        });
    }

    /**
     * Has the decompiler's JVM decompile some of these classes, a JAR just loaded, in the background, so the JIT has
     * compiled FernFlower before the first class is opened: a large class of a game client then takes a quarter less
     * time. The worker stops when a class is asked for. Takes the largest classes up to a size, which make FernFlower
     * do what it does on large classes, until there is a few seconds' work.
     */
    public void warmUp(List<ClassNode> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        // Classes' sizes counted in instructions, three or four bytes each, so only the chosen are written out.
        List<ClassNode> bySize = classes.stream()
            .filter(classNode -> instructions(classNode) <= WARM_UP_MAX_CLASS_INSTRUCTIONS)
            .sorted(Comparator.comparingInt(DecompilerCacheService::instructions).reversed())
            .toList();
        // Snapshot on the calling thread: renames mutate class nodes in place.
        Map<String, byte[]> chosen = new LinkedHashMap<>();
        int total = 0;
        for (ClassNode classNode : bySize) {
            if (total >= WARM_UP_INSTRUCTIONS) {
                break;
            }
            total += instructions(classNode);
            chosen.put(classNode.name, toBytes(classNode));
        }
        classExecutor.execute(() -> worker.warmUp(chosen));
    }

    private static int instructions(ClassNode classNode) {
        int count = 0;
        for (MethodNode method : classNode.methods) {
            count += method.instructions.size();
        }
        return count;
    }

    public synchronized String getDecompiledSource(ClassNode classNode) {
        OriginalLines source = classNode == null ? null : sources.get(classNode);
        return source == null ? null : source.source();
    }

    /**
     * For each line of the class's cached Java, counted from 0, the class file's line numbers it came from; empty
     * when the class has no line numbers or is not decompiled yet.
     */
    public synchronized Map<Integer, List<Integer>> getOriginalLines(ClassNode classNode) {
        OriginalLines source = classNode == null ? null : sources.get(classNode);
        return source == null ? Map.of() : source.originalsByLine();
    }

    /**
     * Drop the cached sources of these classes, a JAR's, and discard any decompilation that is still running.
     */
    public synchronized void invalidate(List<ClassNode> classes) {
        generation++;
        if (classes != null) {
            classes.forEach(sources::remove);
        }
    }

    private synchronized long currentGeneration() {
        return generation;
    }

    private synchronized void cacheSource(ClassNode classNode, OriginalLines source, long startGeneration) {
        if (generation == startGeneration) {
            sources.put(classNode, source);
        }
    }

    private static byte[] toBytes(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    @Override
    public void close() {
        classExecutor.shutdownNow();
        worker.close();
    }
}
