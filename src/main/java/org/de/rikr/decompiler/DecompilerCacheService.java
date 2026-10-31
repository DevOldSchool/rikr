package org.de.rikr.decompiler;

import org.de.rikr.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caches decompiled sources per {@link ClassNode} instance, so classes with the same name in different JARs
 * never share a source. Every rename must call {@link #invalidate}; results computed before an
 * invalidation are discarded.
 */
public class DecompilerCacheService implements AutoCloseable {
    // Classes are decompiled when they are opened, not a whole JAR ahead of time: on a game client that takes
    // minutes and gigabytes, and the first rename throws it all away.
    private final ExecutorService classExecutor;
    private final WorkerDecompiler worker;
    // ClassNode does not override equals/hashCode, so entries are keyed by instance.
    private final Map<ClassNode, OriginalLines> sources = new WeakHashMap<>();
    private long generation;

    public DecompilerCacheService(Logger logger) {
        this(new WorkerDecompiler(logger));
    }

    DecompilerCacheService(WorkerDecompiler worker) {
        this.worker = worker;
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
            DecompileResult decompiled = worker.decompile(internalName, classBytes);
            if (!decompiled.success()) {
                return decompiled;
            }
            OriginalLines source = OriginalLines.parse(decompiled.source());
            cacheSource(classNode, source, startGeneration);
            return DecompileResult.success(source.source());
        }, classExecutor);
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
