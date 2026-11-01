package org.de.rikr.decompiler;

import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

/**
 * Decompiles one class at a time with JetBrains' FernFlower, in memory.
 */
public class JetBrainsDecompilerService {

    /**
     * The longest FernFlower may spend on one method, in seconds; the method is left out of the source after that,
     * so code FernFlower can't finish doesn't hold up the class. Large classes of obfuscated programs take tens of
     * seconds in all, but no single method comes near this. FernFlower checks the limit only now and then.
     */
    private static final int MAX_SECONDS_PER_METHOD = 15;

    private final org.de.rikr.Logger logger;

    public JetBrainsDecompilerService() {
        this(null);
    }

    public JetBrainsDecompilerService(org.de.rikr.Logger logger) {
        this.logger = logger;
    }

    // FernFlower logs from the threads it decompiles methods on.
    private synchronized void log(String msg) {
        if (logger != null) logger.log(msg);
    }

    private synchronized void log(String msg, Throwable t) {
        if (logger != null) {
            logger.log(msg + (t != null ? " -> " + t.getClass().getSimpleName() + ": " + t.getMessage() : ""));
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                logger.log(sw.toString());
            }
        }
    }

    /**
     * Decompile a single ASM ClassNode into readable Java source using FernFlower.
     * Returns null on failure.
     */
    public String decompile(ClassNode classNode) {
        if (classNode == null) {
            log("[Decompiler] classNode is null");
            return null;
        }

        String internalName = classNode.name;
        if (internalName == null || internalName.isEmpty()) {
            log("[Decompiler] internalName is null/empty");
            return null;
        }

        // Convert ClassNode → bytecode
        byte[] classBytes;
        try {
            ClassWriter cw = new ClassWriter(0);
            classNode.accept(cw);
            classBytes = cw.toByteArray();
        } catch (Throwable t) {
            log("[Decompiler] Failed to write class bytes", t);
            return null;
        }

        return decompile(internalName, classBytes);
    }

    /**
     * Decompile the bytes of a single class. Returns null on failure.
     */
    public String decompile(String internalName, byte[] classBytes) {
        // FernFlower reads the class through the provider, by a path that needn't exist, and hands the Java to the
        // saver, so nothing is written to disk. It reads the class again for each method's code.
        String path = new File(internalName.substring(internalName.lastIndexOf('/') + 1) + ".class")
            .getAbsolutePath();
        IBytecodeProvider provider = (externalPath, internalPath) -> {
            if (!path.equals(externalPath)) {
                throw new IOException("No class at " + externalPath);
            }
            return classBytes;
        };
        SourceSaver saver = new SourceSaver();
        IFernflowerLogger fernLogger = new IFernflowerLogger() {
            @Override
            public void writeMessage(String message, Severity severity) {
                log("[FernFlower][" + severity + "] " + message);
            }

            @Override
            public void writeMessage(String message, Severity severity, Throwable t) {
                log("[FernFlower][" + severity + "] " + message, t);
            }
        };

        Fernflower fernflower = new Fernflower(provider, saver, options(), fernLogger,
            new MethodTimeLimit(MAX_SECONDS_PER_METHOD));
        try {
            fernflower.addSource(new File(path));
            fernflower.decompileContext();
            log("[Decompiler] Decompilation completed.");
        } catch (Throwable t) {
            log("[Decompiler] Exception during decompilation", t);
            return null;
        } finally {
            fernflower.clearContext();
        }
        if (saver.source == null) {
            log("[Decompiler] FernFlower wrote no Java for " + internalName);
        }
        return saver.source;
    }

    private static Map<String, Object> options() {
        Map<String, Object> options = new HashMap<>();
        options.put("dgs", "1"); // decompile generic signatures
        options.put("asc", "1"); // encode non-ASCII
        options.put("rsy", "1"); // retain synthetic
        options.put("lit", "1"); // use 'final' for literals
        // Mark each line with the class file's line numbers; OriginalLines takes them out again. The marks need
        // the bytecode-to-source mapping turned on too.
        options.put("bsm", "1");
        options.put("__dump_original_lines__", "1");
        return options;
    }

    /**
     * Stops a method that takes longer than the limit. FernFlower's own keeps one start time, but Rikr's copy of
     * FernFlower decompiles a class's methods in parallel (docs/decisions/0012), so each thread keeps its own.
     */
    private static final class MethodTimeLimit implements CancellationManager {
        private final long limitNanos;
        private final ThreadLocal<Long> started = new ThreadLocal<>();

        MethodTimeLimit(int seconds) {
            limitNanos = TimeUnit.SECONDS.toNanos(seconds);
        }

        @Override
        public void checkCanceled() {
            Long start = started.get();
            if (start != null && System.nanoTime() - start > limitNanos) {
                throw new TimeExceedException();
            }
        }

        @Override
        public void startMethod(String className, String methodName) {
            started.set(System.nanoTime());
        }

        @Override
        public void finishMethod(String className, String methodName) {
            started.remove();
        }
    }

    // Keeps the Java of the one class decompiled; FernFlower calls nothing else for a class added on its own.
    private static final class SourceSaver implements IResultSaver {
        private String source;

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
            source = content;
        }

        @Override
        public void saveFolder(String path) {
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
        }

        @Override
        public void createArchive(String path, String archiveName, Manifest manifest) {
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
        }

        @Override
        public void copyEntry(String source, String path, String archiveName, String entry) {
        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName,
                                   String content) {
        }

        @Override
        public void closeArchive(String path, String archiveName) {
        }
    }
}
