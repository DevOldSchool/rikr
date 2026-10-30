package org.de.rikr.jar;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Writes classes back to JAR files.
 * <p>
 * A JAR is written in two steps. {@link #toEntries} turns the classes into bytes; it reads the class nodes, so it
 * must run on the thread that changes them. {@link #writeJar} and {@link #overwriteOriginalJar} only use those
 * bytes and files, so they can run on any thread.
 * <p>
 * The original JAR's resources and manifest are kept. Its class files are replaced by the classes in memory, so
 * signature files no longer match and are left out, along with the manifest's per-file digests. The new JAR is
 * written to a temporary file next to the destination and moved into place only when complete.
 */
public class JarExportService {

    /**
     * What a JAR export wrote.
     *
     * @param removedSignatureFiles signature files of the original JAR that were left out
     */
    public record ExportResult(int classes, int resources, int removedSignatureFiles) {
    }

    public byte[] toBytes(ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    /**
     * Class file entries for the classes, keyed by entry name in class order. When two classes share a name the
     * first one wins.
     */
    public Map<String, byte[]> toEntries(List<ClassNode> classes) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (ClassNode classNode : classes) {
            entries.putIfAbsent(classNode.name + ".class", toBytes(classNode));
        }
        return Collections.unmodifiableMap(entries);
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

    /**
     * Write a JAR of the class entries plus every resource of {@code original}.
     *
     * @param original the JAR the classes were loaded from, or null for classes without one
     */
    public ExportResult writeJar(File original, File destination, Map<String, byte[]> classEntries) throws IOException {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        Path target = destination.toPath().toAbsolutePath();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "rikr-" + target.getFileName(), ".tmp");
        try {
            ExportResult result;
            try (OutputStream output = Files.newOutputStream(temporary)) {
                result = original == null
                    ? writeClassesOnly(output, classEntries)
                    : writeWithResources(original, output, classEntries);
            }
            moveIntoPlace(temporary, target);
            return result;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Replace the original JAR with the class entries and its resources. A copy of the original is kept as
     * {@code <name>.bak}, made before anything is written, and the original is only replaced once the new JAR is
     * complete.
     */
    public ExportResult overwriteOriginalJar(File original, Map<String, byte[]> classEntries) throws IOException {
        if (original == null || !original.isFile()) {
            throw new IOException("Original JAR does not exist: " + original);
        }
        File backup = new File(original.getParentFile(), original.getName() + ".bak");
        Files.copy(original.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
        return writeJar(original, original, classEntries);
    }

    private ExportResult writeClassesOnly(OutputStream output, Map<String, byte[]> classEntries) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(output)) {
            writeClasses(jar, classEntries, new HashSet<>());
        }
        return new ExportResult(classEntries.size(), 0, 0);
    }

    private ExportResult writeWithResources(File original, OutputStream output, Map<String, byte[]> classEntries)
        throws IOException {
        int resources = 0;
        int removedSignatures = 0;
        try (JarFile source = new JarFile(original, false)) {
            Manifest manifest = source.getManifest();
            // The manifest must come first, so the constructor that writes it is used when there is one.
            try (JarOutputStream jar = manifest == null
                ? new JarOutputStream(output)
                : new JarOutputStream(output, withoutDigests(manifest))) {
                Set<String> written = new HashSet<>();
                if (manifest != null) {
                    written.add(JarFile.MANIFEST_NAME);
                }
                byte[] buffer = new byte[8192];
                Enumeration<JarEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    // Every class file was loaded, so the classes in memory replace all of them.
                    if (name.endsWith(".class") || name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) {
                        continue;
                    }
                    if (isSignatureFile(name)) {
                        removedSignatures++;
                        continue;
                    }
                    if (!written.add(name)) {
                        continue;
                    }
                    JarEntry copy = new JarEntry(name);
                    copy.setTime(entry.getTime());
                    jar.putNextEntry(copy);
                    if (!entry.isDirectory()) {
                        try (InputStream input = source.getInputStream(entry)) {
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                jar.write(buffer, 0, read);
                            }
                        }
                        resources++;
                    }
                    jar.closeEntry();
                }
                writeClasses(jar, classEntries, written);
            }
        }
        return new ExportResult(classEntries.size(), resources, removedSignatures);
    }

    private static void writeClasses(JarOutputStream jar, Map<String, byte[]> classEntries, Set<String> written)
        throws IOException {
        for (Map.Entry<String, byte[]> entry : classEntries.entrySet()) {
            if (!written.add(entry.getKey())) {
                continue;
            }
            jar.putNextEntry(new JarEntry(entry.getKey()));
            jar.write(entry.getValue());
            jar.closeEntry();
        }
    }

    /**
     * Signature files sit directly in META-INF: {@code *.SF}, the {@code *.RSA}, {@code *.DSA} or {@code *.EC}
     * block that signs it, and {@code SIG-*} files.
     */
    static boolean isSignatureFile(String name) {
        String upper = name.toUpperCase();
        if (!upper.startsWith("META-INF/") || upper.indexOf('/', "META-INF/".length()) >= 0) {
            return false;
        }
        String file = upper.substring("META-INF/".length());
        return file.endsWith(".SF") || file.endsWith(".RSA") || file.endsWith(".DSA") || file.endsWith(".EC")
            || file.startsWith("SIG-");
    }

    /**
     * A copy of the manifest without per-file digests, which only describe the signed classes that were replaced.
     */
    private static Manifest withoutDigests(Manifest manifest) {
        Manifest copy = new Manifest(manifest);
        Iterator<Map.Entry<String, Attributes>> entries = copy.getEntries().entrySet().iterator();
        while (entries.hasNext()) {
            Attributes attributes = entries.next().getValue();
            attributes.keySet().removeIf(key -> key.toString().toUpperCase().endsWith("-DIGEST"));
            if (attributes.isEmpty()) {
                entries.remove();
            }
        }
        return copy;
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
