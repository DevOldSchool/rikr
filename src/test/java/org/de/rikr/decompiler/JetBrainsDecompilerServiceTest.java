package org.de.rikr.decompiler;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // Methods are decompiled in parallel (docs/decisions/0012), but the class's imports are still decided in method
    // order: the first method's List is imported and the others' is written in full.
    @Test
    void decompilesMethodsInParallelWithImportsInMethodOrder() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "b";
        classNode.superName = "java/lang/Object";
        classNode.methods.add(localOfType("m0", "java/awt/List"));
        for (int index = 1; index < 40; index++) {
            classNode.methods.add(localOfType("m" + index, "java/util/List"));
        }

        String source = new JetBrainsDecompilerService(message -> {
        }).decompile(classNode);

        assertNotNull(source);
        assertTrue(source.contains("import java.awt.List;"), source);
        assertFalse(source.contains("import java.util.List;"), source);
        for (int index = 0; index < 40; index++) {
            assertTrue(source.contains(" m" + index + "("), source);
        }
        assertEquals(39, source.split("java\\.util\\.List ", -1).length - 1, source);
    }

    // A local of the type, set from a static field so FernFlower keeps it, then returned.
    private static MethodNode localOfType(String name, String type) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()Ljava/lang/Object;",
            null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out",
            "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, type));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out",
            "Ljava/io/PrintStream;"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
            "(Ljava/lang/Object;)V"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 2;
        method.maxLocals = 1;
        return method;
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
