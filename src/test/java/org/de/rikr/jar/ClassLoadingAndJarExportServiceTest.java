package org.de.rikr.jar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoadingAndJarExportServiceTest {
    @TempDir
    Path tempDir;

    private final JarExportService exporter = new JarExportService();

    @Test
    void exportsAndLoadsGeneratedJar() throws Exception {
        ClassNode classNode = simpleClass("fixture/Sample");
        Path jarPath = tempDir.resolve("sample.jar");

        exporter.writeJar(null, jarPath.toFile(), exporter.toEntries(List.of(classNode)));

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertNotNull(jarFile.getEntry("fixture/Sample.class"));
        }

        LoadedJar loadedJar = new ClassLoadingService().readJar(jarPath.toFile(), false);

        assertEquals("sample.jar", loadedJar.jarName());
        assertEquals(1, loadedJar.classes().size());
        assertEquals("fixture/Sample", loadedJar.classes().getFirst().name);
    }

    @Test
    void exportKeepsResourcesAndManifestAndDropsSignatures() throws Exception {
        File original = signedJar(tempDir.resolve("signed.jar"));
        LoadedJar loaded = new ClassLoadingService().readJar(original, false);
        loaded.classes().getFirst().name = "fixture/Renamed";
        File exported = tempDir.resolve("out/renamed.jar").toFile();

        JarExportService.ExportResult result = exporter.writeJar(original, exported, exporter.toEntries(loaded.classes()));

        assertEquals(new JarExportService.ExportResult(1, 1, 2), result);
        try (JarFile jarFile = new JarFile(exported)) {
            assertNotNull(jarFile.getEntry("fixture/Renamed.class"));
            assertNull(jarFile.getEntry("fixture/Sample.class"));
            assertArrayEquals(resource(), jarFile.getInputStream(jarFile.getEntry("assets/data.txt")).readAllBytes());
            assertNull(jarFile.getEntry("META-INF/SIGNER.SF"));
            assertNull(jarFile.getEntry("META-INF/SIGNER.RSA"));
            Manifest manifest = jarFile.getManifest();
            assertEquals("fixture.Sample", manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
            assertTrue(manifest.getEntries().isEmpty(), "per-file digests are removed");
        }
    }

    @Test
    void failedExportLeavesTheDestinationUntouched() throws Exception {
        Path destination = tempDir.resolve("existing.jar");
        Files.writeString(destination, "previous");
        File missingOriginal = tempDir.resolve("missing.jar").toFile();

        assertThrows(IOException.class, () -> exporter.writeJar(
            missingOriginal, destination.toFile(), exporter.toEntries(List.of(simpleClass("fixture/Sample")))));

        assertEquals("previous", Files.readString(destination));
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of("existing.jar"), files.map(path -> path.getFileName().toString()).toList(),
                "no temporary file is left behind");
        }
    }

    @Test
    void overwritesOriginalJarAndKeepsTheOldFileAsBackup() throws Exception {
        File original = signedJar(tempDir.resolve("sample.jar"));
        byte[] before = Files.readAllBytes(original.toPath());
        LoadedJar loaded = new ClassLoadingService().readJar(original, false);
        loaded.classes().getFirst().name = "fixture/Renamed";

        exporter.overwriteOriginalJar(original, exporter.toEntries(loaded.classes()));

        try (JarFile jarFile = new JarFile(original)) {
            assertNotNull(jarFile.getEntry("fixture/Renamed.class"));
            assertNotNull(jarFile.getEntry("assets/data.txt"));
        }
        assertArrayEquals(before, Files.readAllBytes(tempDir.resolve("sample.jar.bak")));
    }

    @Test
    void recognisesSignatureFilesOnlyDirectlyInMetaInf() {
        assertTrue(JarExportService.isSignatureFile("META-INF/CERT.SF"));
        assertTrue(JarExportService.isSignatureFile("meta-inf/cert.rsa"));
        assertTrue(JarExportService.isSignatureFile("META-INF/SIG-EXTRA"));
        assertFalse(JarExportService.isSignatureFile("META-INF/MANIFEST.MF"));
        assertFalse(JarExportService.isSignatureFile("META-INF/services/CERT.SF"));
        assertFalse(JarExportService.isSignatureFile("assets/CERT.SF"));
    }

    private File signedJar(Path path) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "fixture.Sample");
        Attributes digest = new Attributes();
        digest.putValue("SHA-256-Digest", "abc=");
        manifest.getEntries().put("fixture/Sample.class", digest);
        Map<String, byte[]> entries = exporter.toEntries(List.of(simpleClass("fixture/Sample")));
        try (OutputStream output = Files.newOutputStream(path);
             JarOutputStream jar = new JarOutputStream(output, manifest)) {
            put(jar, "META-INF/SIGNER.SF", "signature".getBytes(StandardCharsets.UTF_8));
            put(jar, "META-INF/SIGNER.RSA", new byte[]{1, 2, 3});
            put(jar, "assets/", new byte[0]);
            put(jar, "assets/data.txt", resource());
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                put(jar, entry.getKey(), entry.getValue());
            }
        }
        return path.toFile();
    }

    private static void put(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(bytes);
        jar.closeEntry();
    }

    private static byte[] resource() {
        return "resource data".getBytes(StandardCharsets.UTF_8);
    }

    private ClassNode simpleClass(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";

        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        methodNode.instructions.add(new InsnNode(Opcodes.ICONST_1));
        methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));
        classNode.methods.add(methodNode);

        return classNode;
    }
}
