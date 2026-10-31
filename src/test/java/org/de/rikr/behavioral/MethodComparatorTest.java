package org.de.rikr.behavioral;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodComparatorTest {
    private final MethodComparator comparator = new MethodComparator();

    @Test
    void methodsAreNotEquivalentWhenOnlyOneHasInstructionsRemaining() {
        MethodNode shorterMethod = new MethodNode(Opcodes.ACC_STATIC, "a", "()V", null, null);
        shorterMethod.instructions.add(new InsnNode(Opcodes.ICONST_1));

        MethodNode longerMethod = new MethodNode(Opcodes.ACC_STATIC, "b", "()V", null, null);
        longerMethod.instructions.add(new InsnNode(Opcodes.ICONST_1));
        longerMethod.instructions.add(new InsnNode(Opcodes.NOP));

        assertFalse(comparator.areBehaviorallyEquivalent(null, shorterMethod, null, longerMethod));
    }

    @Test
    void renamedClassesAndFieldsStillMatch() {
        assertTrue(comparator.areBehaviorallyEquivalent(
            "a/Old", fieldTimes("a/Old", "x", 3), "b/New", fieldTimes("b/New", "renamed", 3)));
    }

    @Test
    void differentConstantsDoNotMatch() {
        assertFalse(comparator.areBehaviorallyEquivalent(
            "a/Old", fieldTimes("a/Old", "x", 3), "b/New", fieldTimes("b/New", "x", 4)));
    }

    // this.field * factor
    private static MethodNode fieldTimes(String owner, String field, int factor) {
        MethodNode method = new MethodNode(0, "m", "()I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, field, "I"));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, factor));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }
}
