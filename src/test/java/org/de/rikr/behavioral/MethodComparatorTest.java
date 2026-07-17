package org.de.rikr.behavioral;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MethodComparatorTest {
    @Test
    void methodsAreNotEquivalentWhenOnlyOneHasInstructionsRemaining() {
        MethodNode shorterMethod = new MethodNode(Opcodes.ACC_STATIC, "a", "()V", null, null);
        shorterMethod.instructions.add(new InsnNode(Opcodes.ICONST_1));

        MethodNode longerMethod = new MethodNode(Opcodes.ACC_STATIC, "b", "()V", null, null);
        longerMethod.instructions.add(new InsnNode(Opcodes.ICONST_1));
        longerMethod.instructions.add(new InsnNode(Opcodes.NOP));

        MethodComparator comparator = new MethodComparator();

        assertFalse(comparator.areBehaviorallyEquivalent(
            List.of(),
            shorterMethod,
            List.of(),
            longerMethod
        ));
    }
}
