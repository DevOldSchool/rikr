package org.de.rikr.service;

import org.de.rikr.Logger;
import org.de.rikr.decompiler.JetBrainsDecompilerService;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Caches decompiled sources per {@link ClassNode} instance, so classes with the same name in different JARs
 * never share a source. Every rename must call {@link #invalidateJar}; results computed before an
 * invalidation are discarded.
 */
public class DecompilerCacheService implements AutoCloseable {
    private final Logger logger;
    // Whole-JAR warm-up can take minutes, so classes opened by the user decompile on their own thread.
    private final ExecutorService warmUpExecutor;
    private final ExecutorService classExecutor;
    // ClassNode does not override equals/hashCode, so entries are keyed by instance.
    private final Map<ClassNode, String> sources = new WeakHashMap<>();
    private final Map<String, Long> jarGenerations = new HashMap<>();
    private long generation;

    public DecompilerCacheService(Logger logger) {
        this.logger = logger;
        this.warmUpExecutor = daemonExecutor("rikr-decompiler-warm-up");
        this.classExecutor = daemonExecutor("rikr-decompiler");
    }

    private static ExecutorService daemonExecutor(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Decompile a freshly loaded JAR from disk in the background. Sources are attached to the classes the
     * supplier returns when decompilation finishes, unless the JAR was renamed or removed in the meantime.
     */
    public void warmUpJar(String jarName, File jarFile, Supplier<List<ClassNode>> classes) {
        long startGeneration = jarGeneration(jarName);
        warmUpExecutor.submit(() -> {
            try {
                logger.log("[Decompiler] Warming up sources for JAR: " + jarName);
                JetBrainsDecompilerService service = new JetBrainsDecompilerService(logger);
                Map<String, String> decompiled = service.decompileJarSources(jarFile);
                int cached = cacheWarmUp(jarName, startGeneration, decompiled, classes);
                logger.log("[Decompiler] Warm-up complete for " + jarName + ", sources=" + cached);
            } catch (Throwable ex) {
                logger.log("[Decompiler] Warm-up failed for " + jarName + ": " + ex.getMessage());
            }
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
            JetBrainsDecompilerService service = new JetBrainsDecompilerService(logger);
            String source = service.decompile(internalName, classBytes);
            if (source == null || source.isEmpty()) {
                return DecompileResult.failure("Failed to decompile using JetBrains decompiler.");
            }
            cacheSource(classNode, source, startGeneration);
            return DecompileResult.success(source);
        }, classExecutor);
    }

    public synchronized String getDecompiledSource(ClassNode classNode) {
        return classNode == null ? null : sources.get(classNode);
    }

    /**
     * Drop every cached source of the JAR and discard any decompilation of it that is still running.
     */
    public synchronized void invalidateJar(String jarName, List<ClassNode> classes) {
        generation++;
        jarGenerations.put(jarName, generation);
        if (classes != null) {
            classes.forEach(sources::remove);
        }
    }

    private synchronized long jarGeneration(String jarName) {
        return jarGenerations.getOrDefault(jarName, 0L);
    }

    private synchronized long currentGeneration() {
        return generation;
    }

    private synchronized void cacheSource(ClassNode classNode, String source, long startGeneration) {
        if (generation == startGeneration) {
            sources.put(classNode, source);
        }
    }

    private synchronized int cacheWarmUp(
        String jarName,
        long startGeneration,
        Map<String, String> decompiled,
        Supplier<List<ClassNode>> classes
    ) {
        if (decompiled == null || jarGeneration(jarName) != startGeneration) {
            return 0;
        }
        List<ClassNode> current = classes.get();
        if (current == null) {
            return 0;
        }
        int cached = 0;
        for (ClassNode classNode : current) {
            String source = decompiled.get(classNode.name);
            if (source != null && !source.isEmpty()) {
                sources.putIfAbsent(classNode, source);
                cached++;
            }
        }
        return cached;
    }

    private static byte[] toBytes(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    @Override
    public void close() {
        warmUpExecutor.shutdownNow();
        classExecutor.shutdownNow();
    }
}
