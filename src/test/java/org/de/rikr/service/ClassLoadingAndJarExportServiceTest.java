package org.de.rikr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoadingAndJarExportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsAndLoadsGeneratedJar() throws Exception {
        ClassNode classNode = simpleClass("fixture/Sample");
        JarExportService exportService = new JarExportService(message -> {
        });
        Path jarPath = tempDir.resolve("sample.jar");

        exportService.exportJar(jarPath.toFile(), List.of(classNode));

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertNotNull(jarFile.getEntry("fixture/Sample.class"));
        }

        LoadedJar loadedJar = new ClassLoadingService().readJar(jarPath.toFile(), false);

        assertEquals("sample.jar", loadedJar.jarName());
        assertEquals(1, loadedJar.classes().size());
        assertEquals("fixture/Sample", loadedJar.classes().getFirst().name);
    }

    @Test
    void overwritesOriginalJarWithBackup() throws Exception {
        ClassNode classNode = simpleClass("fixture/Sample");
        JarExportService exportService = new JarExportService(message -> {
        });
        Path jarPath = tempDir.resolve("sample.jar");

        exportService.exportJar(jarPath.toFile(), List.of(classNode));
        classNode.name = "fixture/Renamed";

        assertTrue(exportService.overwriteOriginalJar(jarPath.toFile(), List.of(classNode)));

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertNotNull(jarFile.getEntry("fixture/Renamed.class"));
        }
        assertTrue(tempDir.resolve("sample.jar.bak").toFile().exists());
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
