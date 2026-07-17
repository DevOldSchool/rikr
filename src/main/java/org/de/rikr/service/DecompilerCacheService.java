package org.de.rikr.service;

import org.de.rikr.Logger;
import org.de.rikr.decompiler.JetBrainsDecompilerService;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DecompilerCacheService implements AutoCloseable {
    private final Logger logger;
    private final ExecutorService executor;
    private final Map<String, Map<String, String>> decompiledByJar = new ConcurrentHashMap<>();
    private final Map<String, String> decompiledByClass = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> warmups = new ConcurrentHashMap<>();

    public DecompilerCacheService(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rikr-decompiler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void warmUpJar(File jarFile) {
        String jarName = jarFile.getName();
        AtomicBoolean started = warmups.computeIfAbsent(jarName, ignored -> new AtomicBoolean(false));
        if (!started.compareAndSet(false, true)) {
            return;
        }

        executor.submit(() -> {
            try {
                logger.log("[Decompiler] Warming up sources for JAR: " + jarName);
                JetBrainsDecompilerService service = new JetBrainsDecompilerService(logger);
                Map<String, String> sources = service.decompileJarSources(jarFile);
                cacheDecompiledSources(jarName, sources);
                logger.log("[Decompiler] Warm-up complete for " + jarName + ", sources=" + sources.size());
            } catch (Throwable ex) {
                logger.log("[Decompiler] Warm-up failed for " + jarName + ": " + ex.getMessage());
            }
        });
    }

    public CompletableFuture<DecompileResult> decompileClassAsync(ClassNode classNode) {
        if (classNode == null || classNode.name == null) {
            return CompletableFuture.completedFuture(DecompileResult.failure("Class node is missing."));
        }

        String cached = getDecompiledSourceByClass(classNode.name);
        if (cached != null && !cached.isEmpty()) {
            return CompletableFuture.completedFuture(DecompileResult.success(cached));
        }

        return CompletableFuture.supplyAsync(() -> {
            JetBrainsDecompilerService service = new JetBrainsDecompilerService(logger);
            String source = service.decompile(classNode);
            if (source == null || source.isEmpty()) {
                return DecompileResult.failure("Failed to decompile using JetBrains decompiler.");
            }
            decompiledByClass.put(classNode.name, source);
            return DecompileResult.success(source);
        }, executor);
    }

    public String getDecompiledSourceByClass(String internalName) {
        if (internalName == null) {
            return null;
        }

        return decompiledByClass.get(internalName);
    }

    private void cacheDecompiledSources(String jarName, Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        decompiledByJar.put(jarName, Map.copyOf(sources));

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            decompiledByClass.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
