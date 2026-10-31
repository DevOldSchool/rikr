package org.de.rikr.decompiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JetBrainsDecompilerServiceTest {

    // Obfuscators build arrays like these in code that never runs. Unpatched, FernFlower made an initializer of
    // 940935738 elements for the first, filling any heap, and threw on the negative index of the second.
    @Test
    void decompilesArraysWithObfuscatedSizesAndIndices() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "a";
        classNode.superName = "java/lang/Object";
        classNode.methods.add(arrayOnStack("huge", 940_935_738, 0, 1, 2));
        classNode.methods.add(arrayOnStack("negative", 3, 0, 1, -608_479_275));

        String source = assertTimeoutPreemptively(Duration.ofSeconds(30),
            () -> new JetBrainsDecompilerService(message -> {
            }).decompile(classNode));

        assertNotNull(source);
        assertFalse(source.contains("Couldn't be decompiled"), source);
        assertTrue(source.contains("new Object[940935738]"), source);
        assertTrue(source.contains("[-608479275] = "), source);
    }

    // new Object[size], then each index set to System.out, all on the operand stack.
    private static MethodNode arrayOnStack(String name, int size, int... indices) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()[Ljava/lang/Object;",
            null, null);
        method.instructions.add(new LdcInsnNode(size));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        for (int index : indices) {
            method.instructions.add(new InsnNode(Opcodes.DUP));
            method.instructions.add(new LdcInsnNode(index));
            method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;"));
            method.instructions.add(new InsnNode(Opcodes.AASTORE));
        }
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 4;
        return method;
    }
}
