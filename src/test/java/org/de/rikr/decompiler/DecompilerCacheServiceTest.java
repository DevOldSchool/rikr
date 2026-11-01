package org.de.rikr.decompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerCacheServiceTest {
    @Test
    void returnsStructuredFailureForMissingClass() {
        try (DecompilerCacheService service = new DecompilerCacheService(message -> {
        })) {
            DecompileResult result = service.decompileClassAsync(null).join();

            assertFalse(result.success());
            assertNotNull(result.message());
        }
    }

    @Test
    void cachesPerClassInstanceAndDropsSourcesOnInvalidation() {
        ClassNode first = simpleClass("a");
        ClassNode sameNameInOtherJar = simpleClass("a");
        try (DecompilerCacheService service = new DecompilerCacheService(message -> {
        })) {
            assertTrue(service.decompileClassAsync(first).join().success());

            assertNotNull(service.getDecompiledSource(first));
            assertNull(service.getDecompiledSource(sameNameInOtherJar));

            service.invalidate(List.of(first));

            assertNull(service.getDecompiledSource(first));
        }
    }

    @Test
    void decompilesAClassOnceAcrossRuns(@TempDir Path directory) {
        List<String> log = new CopyOnWriteArrayList<>();
        ClassNode classNode = simpleClass("c");
        try (DecompilerCacheService service = new DecompilerCacheService(
            new WorkerDecompiler(log::add, null, Duration.ofSeconds(60)),
            new SourceCache(directory, SourceCache.DEFAULT_LIMIT_BYTES, log::add), log::add)) {
            assertTrue(service.decompileClassAsync(classNode).join().success());
        }
        assertTrue(log.stream().anyMatch(message -> message.contains("Decompilation completed")), log.toString());
        log.clear();

        // A new run finds the Java of the same class, from another class node, without decompiling it.
        try (DecompilerCacheService service = new DecompilerCacheService(
            new WorkerDecompiler(log::add, null, Duration.ofSeconds(60)),
            new SourceCache(directory, SourceCache.DEFAULT_LIMIT_BYTES, log::add), log::add)) {
            DecompileResult result = service.decompileClassAsync(simpleClass("c")).join();

            assertTrue(result.success(), result.message());
            assertTrue(result.source().contains("class c"), result.source());
        }
        assertFalse(log.stream().anyMatch(message -> message.contains("Decompilation completed")), log.toString());
    }

    @Test
    void keepsJavaInTheFolderConfigured(@TempDir Path first, @TempDir Path second) throws IOException {
        List<String> log = new CopyOnWriteArrayList<>();
        // The build sets it to keep tests out of the user's cache, and it wins over the folder configured.
        String override = System.clearProperty(SourceCache.DIRECTORY_PROPERTY);
        try (DecompilerCacheService service = new DecompilerCacheService(
            new WorkerDecompiler(log::add, null, Duration.ofSeconds(60)),
            new SourceCache(first, SourceCache.DEFAULT_LIMIT_BYTES, log::add), log::add)) {
            service.configureDiskCache(second, SourceCache.DEFAULT_LIMIT_BYTES);

            assertTrue(service.decompileClassAsync(simpleClass("d")).join().success());
        } finally {
            if (override != null) {
                System.setProperty(SourceCache.DIRECTORY_PROPERTY, override);
            }
        }

        assertEquals(0, entries(first));
        assertEquals(1, entries(second));
    }

    private static long entries(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".java.gz")).count();
        }
    }

    private ClassNode simpleClass(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        classNode.methods.add(method);
        return classNode;
    }
}
