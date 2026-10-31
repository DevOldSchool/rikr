package org.de.rikr.decompiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerDecompilerTest {
    private final List<String> log = new CopyOnWriteArrayList<>();

    @Test
    void decompilesInItsOwnProcessAndStartsAgainAfterItIsStopped() {
        List<String> command = WorkerDecompiler.command();
        assertNotNull(command, "The test JVM has a java launcher");
        try (WorkerDecompiler worker = new WorkerDecompiler(log::add, command, Duration.ofSeconds(60))) {
            DecompileResult first = worker.decompile("a", classBytes("a"));
            assertTrue(first.success(), first.message());
            assertTrue(first.source().contains("class a"), first.source());
            // The worker's log reaches this side.
            assertTrue(log.stream().anyMatch(message -> message.startsWith("[Decompiler]")), log.toString());

            worker.close();
            DecompileResult second = worker.decompile("b", classBytes("b"));
            assertTrue(second.success(), second.message());
            assertTrue(second.source().contains("class b"), second.source());
        }
    }

    @Test
    void stopsAClassThatTakesTooLong() {
        // Starting a JVM alone takes longer than this.
        try (WorkerDecompiler worker = new WorkerDecompiler(log::add, WorkerDecompiler.command(),
            Duration.ofMillis(1))) {
            DecompileResult result = worker.decompile("a", classBytes("a"));

            assertFalse(result.success());
            assertTrue(result.message().contains("took longer than"), result.message());
        }
    }

    @Test
    void decompilesInThisJvmWithoutALauncher() {
        try (WorkerDecompiler worker = new WorkerDecompiler(log::add, null, Duration.ofSeconds(60))) {
            DecompileResult result = worker.decompile("a", classBytes("a"));

            assertTrue(result.success(), result.message());
            assertTrue(result.source().contains("class a"), result.source());
        }
    }

    @Test
    void decompilesInThisJvmWhenTheProcessCantStart() {
        try (WorkerDecompiler worker = new WorkerDecompiler(log::add, List.of("/no/such/java"),
            Duration.ofSeconds(60))) {
            DecompileResult result = worker.decompile("a", classBytes("a"));

            assertTrue(result.success(), result.message());
            assertTrue(log.stream().anyMatch(message -> message.contains("Can't start a decompiler process")),
                log.toString());
        }
    }

    private static byte[] classBytes(String name) {
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
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
