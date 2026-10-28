package org.de.rikr;

import org.de.rikr.service.JarExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void refusesSecondJarWithTheSameFileName() throws Exception {
        File first = writeJar("one", "first/Only");
        File second = writeJar("two", "second/Only");
        List<String> log = new ArrayList<>();

        try (ClassProcessor processor = new ClassProcessor(log::add)) {
            processor.processJarFiles(new File[]{first, second});

            assertEquals("first/Only", processor.getClasses("gamepack.jar").getFirst().name);
            assertEquals(first.getAbsoluteFile(), processor.getOriginalJarFile("gamepack.jar"));
            assertTrue(log.stream().anyMatch(message -> message.contains("already loaded")));
        }
    }

    private File writeJar(String directory, String className) throws Exception {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = className;
        classNode.superName = "java/lang/Object";
        File jar = tempDir.resolve(directory).resolve("gamepack.jar").toFile();
        new JarExportService(message -> {
        }).exportJar(jar, List.of(classNode));
        return jar;
    }
}
