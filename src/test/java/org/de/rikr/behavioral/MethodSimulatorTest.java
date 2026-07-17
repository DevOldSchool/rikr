package org.de.rikr.behavioral;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodSimulatorTest {
    @Test
    void stepsThroughSimpleArithmeticStackState() {
        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sum", "()I", null, null);
        methodNode.instructions.add(new InsnNode(Opcodes.ICONST_1));
        methodNode.instructions.add(new InsnNode(Opcodes.ICONST_2));
        methodNode.instructions.add(new InsnNode(Opcodes.IADD));

        MethodSimulator simulator = new MethodSimulator(List.of());

        simulator.stepForwards(methodNode);
        simulator.stepForwards(methodNode);
        simulator.stepForwards(methodNode);

        assertEquals(1, simulator.getStack().size());
        assertEquals(3, simulator.getStack().peek());
    }
}
