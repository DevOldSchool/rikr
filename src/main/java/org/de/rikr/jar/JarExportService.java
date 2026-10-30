package org.de.rikr.jar;

import org.de.rikr.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarExportService {
    private final Logger logger;

    public JarExportService(Logger logger) {
        this.logger = logger;
    }

    public byte[] toBytes(ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    public void saveClassToFile(ClassNode classNode, File dest) throws IOException {
        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directories: " + parent);
        }

        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(toBytes(classNode));
        }
    }

    public void exportJar(File destJar, List<ClassNode> classes) throws IOException {
        if (destJar == null) {
            throw new IllegalArgumentException("destJar is null");
        }

        File parent = destJar.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directories: " + parent);
        }

        Set<String> seen = new HashSet<>();
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(destJar))) {
            for (ClassNode classNode : classes) {
                String name = classNode.name + ".class";
                if (!seen.add(name)) {
                    continue;
                }

                JarEntry entry = new JarEntry(name);
                jarOutputStream.putNextEntry(entry);
                jarOutputStream.write(toBytes(classNode));
                jarOutputStream.closeEntry();
            }
        }

        logger.log("Saved " + seen.size() + " classes to JAR: " + destJar.getAbsolutePath());
    }

    public boolean overwriteOriginalJar(File original, List<ClassNode> classes) throws IOException {
        if (original == null || classes == null) {
            return false;
        }

        Map<String, byte[]> replacements = new HashMap<>();
        for (ClassNode classNode : classes) {
            replacements.put(classNode.name + ".class", toBytes(classNode));
        }

        File backup = new File(original.getParentFile(), original.getName() + ".bak");
        File temp = new File(original.getParentFile(), original.getName() + ".rikr.tmp");

        writeReplacementJar(original, temp, classes, replacements);

        if (backup.exists()) {
            Files.delete(backup.toPath());
        }

        Files.move(original.toPath(), backup.toPath());
        Files.move(temp.toPath(), original.toPath());
        logger.log("Overwrote JAR: " + original.getAbsolutePath() + " (backup at " + backup.getName() + ")");

        return true;
    }

    private void writeReplacementJar(
        File original,
        File temp,
        List<ClassNode> classes,
        Map<String, byte[]> replacements
    ) throws IOException {
        try (JarFile jarFile = new JarFile(original);
             JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(temp))) {
            byte[] buffer = new byte[8192];
            Set<String> written = new HashSet<>();
            Set<String> targetClassEntries = new HashSet<>();

            for (ClassNode classNode : classes) {
                targetClassEntries.add(classNode.name + ".class");
            }

            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !targetClassEntries.contains(name) && !replacements.containsKey(name)) {
                    continue;
                }

                JarEntry newEntry = new JarEntry(name);
                jarOutputStream.putNextEntry(newEntry);
                byte[] replacement = replacements.get(name);
                if (replacement != null) {
                    jarOutputStream.write(replacement);
                    written.add(name);
                } else {
                    copyEntry(jarFile, entry, jarOutputStream, buffer);
                }
                jarOutputStream.closeEntry();
            }

            for (Map.Entry<String, byte[]> entry : replacements.entrySet()) {
                if (!written.contains(entry.getKey())) {
                    JarEntry newEntry = new JarEntry(entry.getKey());
                    jarOutputStream.putNextEntry(newEntry);
                    jarOutputStream.write(entry.getValue());
                    jarOutputStream.closeEntry();
                }
            }
        }
    }

    private void copyEntry(
        JarFile jarFile,
        JarEntry entry,
        JarOutputStream jarOutputStream,
        byte[] buffer
    ) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                jarOutputStream.write(buffer, 0, read);
            }
        }
    }
}
