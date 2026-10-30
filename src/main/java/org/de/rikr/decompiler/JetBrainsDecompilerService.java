package org.de.rikr.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Simple, reliable service for decompiling ASM ClassNodes using JetBrains' FernFlower (ConsoleDecompiler).
 * This version wraps the class bytes into a temporary JAR, runs the decompiler, then extracts the
 * resulting .java files from the decompiled output JAR.
 */
public class JetBrainsDecompilerService {

    private final org.de.rikr.Logger logger;

    public JetBrainsDecompilerService() {
        this(null);
    }

    public JetBrainsDecompilerService(org.de.rikr.Logger logger) {
        this.logger = logger;
    }

    private void log(String msg) {
        if (logger != null) logger.log(msg);
    }

    private void log(String msg, Throwable t) {
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
        Path tempRoot;
        try {
            tempRoot = Files.createTempDirectory("rikr_decompile");
        } catch (IOException e) {
            log("[Decompiler] Failed to create temp directory", e);
            return null;
        }

        Path inputDir = tempRoot.resolve("input");
        Path outputDir = tempRoot.resolve("output");

        try {
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);

            // === Create temporary input JAR ===
            Path jarFile = inputDir.resolve("temp.jar");
            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
                jos.putNextEntry(new JarEntry(internalName + ".class"));
                jos.write(classBytes);
                jos.closeEntry();
            }
            log("[Decompiler] Created temp JAR: " + jarFile);

            // === Configure FernFlower ===
            Map<String, Object> options = new HashMap<>();
            options.put("dgs", "1"); // decompile generic signatures
            options.put("asc", "1"); // encode non-ASCII
            options.put("rsy", "1"); // retain synthetic
            options.put("lit", "1"); // use 'final' for literals

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

            // === Run the decompiler ===
            ConsoleDecompiler decompiler = new ConsoleDecompiler(outputDir.toFile(), options, fernLogger);
            decompiler.addSource(jarFile.toFile());
            decompiler.decompileContext();
            log("[Decompiler] Decompilation completed.");

            // === Locate FernFlower's output JAR ===
            Path outputJar = outputDir.resolve("temp.jar");
            if (!Files.exists(outputJar)) {
                // Try to find any .jar in outputDir
                try (var stream = Files.list(outputDir)) {
                    outputJar = stream.filter(p -> p.toString().endsWith(".jar")).findFirst().orElse(null);
                }
            }

            if (outputJar == null || !Files.exists(outputJar)) {
                log("[Decompiler] No decompiled JAR found in " + outputDir);
                return null;
            }

            // === Extract .java files ===
            Path extractDir = outputDir.resolve("extracted");
            Files.createDirectories(extractDir);
            extractJar(outputJar.toFile(), extractDir.toFile());

            // === Find the first .java file and return its contents ===
            Path javaFile;
            try (var stream = Files.walk(extractDir)) {
                javaFile = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .findFirst()
                    .orElse(null);
            }

            if (javaFile == null) {
                log("[Decompiler] No .java files found after extraction");
                return null;
            }

            String source = Files.readString(javaFile, StandardCharsets.UTF_8);

            // Delete the output JAR after extraction
            try {
                Files.deleteIfExists(outputJar);
                log("[Decompiler] Deleted output JAR: " + outputJar);
            } catch (IOException ignored) {
            }

            return source;

        } catch (Throwable t) {
            log("[Decompiler] Exception during decompilation", t);
            return null;
        } finally {
            try {
                deleteRecursive(tempRoot);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Extracts all entries from a JAR/ZIP file into a target directory.
     */
    private void extractJar(File zipFile, File outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(newFile))) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteRecursive(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted((a, b) -> b.compareTo(a)) // delete children before parents
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    /**
     * Decompile an entire JAR and return a map from ASM internal class name (e.g., com/foo/Bar)
     * to the resulting Java source code.
     */
    public Map<String, String> decompileJarSources(File jarFile) {
        Path tempRoot = null;
        try {
            if (jarFile == null || !jarFile.exists()) {
                log("[Decompiler] decompileJarSources: input jar is null or does not exist");
                return java.util.Collections.emptyMap();
            }

            tempRoot = Files.createTempDirectory("rikr_ff_jar");
            Path outputDir = tempRoot.resolve("output");
            Files.createDirectories(outputDir);

            Map<String, Object> options = new HashMap<>();
            options.put("dgs", "1");
            options.put("asc", "1");
            options.put("rsy", "1");
            options.put("lit", "1");

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

            ConsoleDecompiler decompiler = new ConsoleDecompiler(outputDir.toFile(), options, fernLogger);
            decompiler.addSource(jarFile);
            decompiler.decompileContext();

            // Find output jar produced by FernFlower
            Path outputJar = null;
            try (var stream = Files.list(outputDir)) {
                outputJar = stream.filter(p -> p.toString().endsWith(".jar")).findFirst().orElse(null);
            }

            if (outputJar == null || !Files.exists(outputJar)) {
                log("[Decompiler] decompileJarSources: no output jar produced for " + jarFile.getName());
                return java.util.Collections.emptyMap();
            }

            Path extractDir = outputDir.resolve("extracted");
            Files.createDirectories(extractDir);
            extractJar(outputJar.toFile(), extractDir.toFile());

            Map<String, String> result = new HashMap<>();
            try (var stream = Files.walk(extractDir)) {
                stream.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String src = Files.readString(p, StandardCharsets.UTF_8);
                        String internal = toInternalName(extractDir, p);
                        if (internal != null && !internal.isEmpty()) {
                            result.put(internal, src);
                        }
                    } catch (IOException e) {
                        log("[Decompiler] Failed reading java file: " + p + " -> " + e.getMessage());
                    }
                });
            }

            try {
                Files.deleteIfExists(outputJar);
            } catch (IOException ignored) {
            }
            return result;
        } catch (Throwable t) {
            log("[Decompiler] Exception during full-jar decompilation", t);
            return java.util.Collections.emptyMap();
        } finally {
            try {
                deleteRecursive(tempRoot);
            } catch (IOException ignored) {
            }
        }
    }

    private String toInternalName(Path root, Path javaFile) {
        try {
            Path rel = root.relativize(javaFile);
            String s = rel.toString();
            s = s.replace(File.separatorChar, '/');
            if (s.endsWith(".java")) s = s.substring(0, s.length() - 5);
            return s; // now in form com/foo/Bar
        } catch (Exception e) {
            return null;
        }
    }
}
