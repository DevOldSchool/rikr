package org.de.rikr.behavioral;

import org.de.rikr.behavioral.SimulatedValue.ArrayRef;
import org.de.rikr.behavioral.SimulatedValue.ObjectRef;
import org.de.rikr.behavioral.SimulatedValue.Unknown;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodSimulatorTest {
    private static MethodNode method(int access, String descriptor, AbstractInsnNode... instructions) {
        MethodNode method = new MethodNode(access, "run", descriptor, null, null);
        for (AbstractInsnNode instruction : instructions) {
            method.instructions.add(instruction);
        }
        return method;
    }

    private static MethodNode staticMethod(AbstractInsnNode... instructions) {
        return method(Opcodes.ACC_STATIC, "()V", instructions);
    }

    private static MethodSimulator runAll(MethodSimulator simulator) {
        while (simulator.stepForwards()) {
            // Run to the end.
        }
        return simulator;
    }

    private static Object top(MethodSimulator simulator) {
        List<Object> stack = simulator.getStack();
        return stack.get(stack.size() - 1);
    }

    @Test
    void addsKnownNumbers() {
        MethodSimulator simulator = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_1), new InsnNode(Opcodes.ICONST_2), new InsnNode(Opcodes.IADD))));

        assertEquals(List.of(3), simulator.getStack());
    }

    @Test
    void anythingComputedFromAnUnknownValueIsUnknownAndKeepsTheStackShape() {
        // byte[] p; (p[0] & 31): the array's contents are unknown, so the result is too, not 31.
        MethodNode method = method(Opcodes.ACC_STATIC, "([B)I",
            new IntInsnNode(Opcodes.BIPUSH, 5),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new InsnNode(Opcodes.ICONST_0),
            new InsnNode(Opcodes.BALOAD),
            new IntInsnNode(Opcodes.BIPUSH, 31),
            new InsnNode(Opcodes.IAND));
        MethodSimulator simulator = runAll(new MethodSimulator(null, method));

        assertEquals(2, simulator.getStack().size());
        assertEquals(5, simulator.getStack().get(0));
        assertEquals(new Unknown(Type.INT_TYPE), top(simulator));
    }

    @Test
    void startsWithThisAndEachParameterInItsSlot() {
        MethodNode method = method(0, "(JLjava/lang/String;)V");
        MethodSimulator simulator = new MethodSimulator("a/Owner", method);

        assertEquals(new ObjectRef(0, Type.getObjectType("a/Owner")), simulator.getLocalVariables().get(0));
        assertEquals(new Unknown(Type.LONG_TYPE, "parameter 1"), simulator.getLocalVariables().get(1));
        // A long takes two slots, so the string is in slot 3.
        assertEquals(new Unknown(Type.getType(String.class), "parameter 2"), simulator.getLocalVariables().get(3));
    }

    @Test
    void remembersFieldsItWritesUntilACallMightChangeThem() {
        MethodNode method = method(0, "()V",
            new VarInsnNode(Opcodes.ALOAD, 0),
            new FieldInsnNode(Opcodes.GETFIELD, "a/Owner", "count", "I"),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new IntInsnNode(Opcodes.BIPUSH, 9),
            new FieldInsnNode(Opcodes.PUTFIELD, "a/Owner", "count", "I"),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new FieldInsnNode(Opcodes.GETFIELD, "a/Owner", "count", "I"),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a/Owner", "change", "()V", false),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new FieldInsnNode(Opcodes.GETFIELD, "a/Owner", "count", "I"));
        MethodSimulator simulator = runAll(new MethodSimulator("a/Owner", method));

        List<Object> stack = simulator.getStack();
        assertEquals(new Unknown(Type.INT_TYPE, "this.count"), stack.get(0));
        assertEquals(9, stack.get(1));
        assertEquals(new Unknown(Type.INT_TYPE, "this.count"), stack.get(2));
    }

    @Test
    void callsPopTheirArgumentsAndLeaveAnUnknownResult() {
        MethodSimulator simulator = new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_1),
            new InsnNode(Opcodes.ICONST_2),
            new MethodInsnNode(Opcodes.INVOKESTATIC, "a/Util", "max", "(II)I", false)));
        runAll(simulator);

        assertEquals(List.of(new Unknown(Type.INT_TYPE, "Util.max()")), simulator.getStack());
        assertTrue(simulator.note().contains("Util.max"));
    }

    @Test
    void storesAndLoadsArrayElements() {
        MethodSimulator simulator = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_3),
            new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE),
            new VarInsnNode(Opcodes.ASTORE, 0),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new InsnNode(Opcodes.ICONST_1),
            new IntInsnNode(Opcodes.SIPUSH, 200),
            new InsnNode(Opcodes.BASTORE),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new InsnNode(Opcodes.ICONST_1),
            new InsnNode(Opcodes.BALOAD),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new InsnNode(Opcodes.ICONST_2),
            new InsnNode(Opcodes.BALOAD),
            new VarInsnNode(Opcodes.ALOAD, 0),
            new InsnNode(Opcodes.ARRAYLENGTH))));

        // 200 stored in a byte array reads back as -56; an element never written is 0.
        assertEquals(List.of(-56, 0, 3), simulator.getStack());
        assertInstanceOf(ArrayRef.class, simulator.getLocalVariables().get(0));
    }

    @Test
    void stopsWithTheExceptionTheJvmWouldThrow() {
        MethodSimulator outOfBounds = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_1),
            new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT),
            new InsnNode(Opcodes.ICONST_5),
            new InsnNode(Opcodes.IALOAD))));
        assertTrue(outOfBounds.isFinished());
        assertTrue(outOfBounds.note().startsWith("Throws ArrayIndexOutOfBoundsException"));
        assertEquals(3, outOfBounds.lastPosition());

        MethodSimulator byZero = runAll(new MethodSimulator(null, method(Opcodes.ACC_STATIC, "(I)V",
            new VarInsnNode(Opcodes.ILOAD, 0), new InsnNode(Opcodes.ICONST_0), new InsnNode(Opcodes.IDIV))));
        assertEquals("Throws ArithmeticException: / by zero.", byZero.note());

        MethodSimulator onNull = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ACONST_NULL), new FieldInsnNode(Opcodes.GETFIELD, "a/B", "c", "I"))));
        assertTrue(onNull.note().startsWith("Throws NullPointerException"));
    }

    @Test
    void returnsLeaveTheirValueOnTheStack() {
        MethodSimulator simulator = runAll(new MethodSimulator(null, method(Opcodes.ACC_STATIC, "()I",
            new InsnNode(Opcodes.ICONST_5), new InsnNode(Opcodes.IRETURN), new InsnNode(Opcodes.NOP))));

        assertTrue(simulator.isFinished());
        assertEquals("Returns 5.", simulator.note());
        assertEquals(List.of(5), simulator.getStack());
        assertEquals(-1, simulator.position());
        assertEquals(1, simulator.lastPosition());
    }

    @Test
    void duplicatesWordsTheWayTheJvmDoes() {
        MethodSimulator wide = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.LCONST_1), new InsnNode(Opcodes.DUP2), new InsnNode(Opcodes.LADD))));
        assertEquals(List.of(2L), wide.getStack());

        MethodSimulator narrow = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_1), new InsnNode(Opcodes.ICONST_2), new InsnNode(Opcodes.DUP_X1))));
        assertEquals(List.of(2, 1, 2), narrow.getStack());

        MethodSimulator mixed = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.LCONST_0), new InsnNode(Opcodes.ICONST_3), new InsnNode(Opcodes.DUP_X2))));
        assertEquals(List.of(3, 0L, 3), mixed.getStack());

        // Nulls are values too.
        MethodSimulator nulls = runAll(new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ACONST_NULL), new InsnNode(Opcodes.DUP), new InsnNode(Opcodes.POP))));
        assertEquals(Collections.singletonList(null), nulls.getStack());
    }

    @Test
    void convertsAndComparesLikeJava() {
        MethodSimulator simulator = runAll(new MethodSimulator(null, staticMethod(
            new IntInsnNode(Opcodes.SIPUSH, 200), new InsnNode(Opcodes.I2B),
            new InsnNode(Opcodes.LCONST_0), new InsnNode(Opcodes.LCONST_1), new InsnNode(Opcodes.LCMP),
            new InsnNode(Opcodes.FCONST_0), new InsnNode(Opcodes.FCONST_0), new InsnNode(Opcodes.FDIV),
            new InsnNode(Opcodes.FCONST_1), new InsnNode(Opcodes.FCMPG))));

        // 0/0 is NaN, which FCMPG compares as greater.
        assertEquals(List.of(-56, -1, 1), simulator.getStack());
    }

    @Test
    void takesAJumpItCanDecideAndAsksAboutOneItCannot() {
        LabelNode target = new LabelNode();
        MethodNode method = method(Opcodes.ACC_STATIC, "(I)I",
            new VarInsnNode(Opcodes.ILOAD, 0),
            new JumpInsnNode(Opcodes.IFEQ, target),
            new InsnNode(Opcodes.ICONST_1),
            new InsnNode(Opcodes.IRETURN),
            target,
            new InsnNode(Opcodes.ICONST_0),
            new JumpInsnNode(Opcodes.IFEQ, new LabelNode()),
            new InsnNode(Opcodes.ICONST_2),
            new InsnNode(Opcodes.IRETURN));
        method.instructions.add(((JumpInsnNode) method.instructions.get(6)).label);
        method.instructions.add(new InsnNode(Opcodes.ICONST_3));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        MethodSimulator simulator = new MethodSimulator(null, method);

        simulator.stepForwards();
        assertTrue(simulator.isUndecidedBranch());
        simulator.stepForwards(true);
        assertTrue(simulator.note().contains("you chose to jump"));
        assertEquals(5, simulator.position());

        // ICONST_0 then IFEQ is decided: it jumps.
        simulator.stepForwards();
        assertFalse(simulator.isUndecidedBranch());
        simulator.stepForwards();
        assertEquals(10, simulator.position());

        simulator.stepBackwards();
        simulator.stepBackwards();
        simulator.stepBackwards();
        simulator.stepForwards(false);
        assertEquals(2, simulator.position());
    }

    @Test
    void followsSwitchesAndTakesTheDefaultForAnUnknownKey() {
        LabelNode one = new LabelNode();
        LabelNode other = new LabelNode();
        MethodSimulator known = new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_1),
            new TableSwitchInsnNode(0, 1, other, other, one),
            other, new InsnNode(Opcodes.RETURN), one, new InsnNode(Opcodes.RETURN)));
        known.stepForwards();
        known.stepForwards();
        assertEquals(5, known.position());

        LabelNode found = new LabelNode();
        LabelNode fallback = new LabelNode();
        MethodSimulator unknown = new MethodSimulator(null, method(Opcodes.ACC_STATIC, "(I)V",
            new VarInsnNode(Opcodes.ILOAD, 0),
            new LookupSwitchInsnNode(fallback, new int[]{7}, new LabelNode[]{found}),
            found, new InsnNode(Opcodes.RETURN), fallback, new InsnNode(Opcodes.RETURN)));
        unknown.stepForwards();
        unknown.stepForwards();
        assertEquals(5, unknown.position());
        assertTrue(unknown.note().contains("default case"));
    }

    @Test
    void steppingBackwardsRestoresEverything() {
        MethodNode method = staticMethod(
            new InsnNode(Opcodes.ICONST_5),
            new VarInsnNode(Opcodes.ISTORE, 1),
            new InsnNode(Opcodes.RETURN));
        MethodSimulator simulator = new MethodSimulator(null, method);
        simulator.stepForwards();
        simulator.stepForwards();
        simulator.stepForwards();
        assertTrue(simulator.isFinished());

        assertTrue(simulator.stepBackwards());
        assertFalse(simulator.isFinished());
        assertEquals(5, simulator.getLocalVariables().get(1));
        assertTrue(simulator.stepBackwards());
        assertFalse(simulator.getLocalVariables().containsKey(1));
        assertEquals(List.of(5), simulator.getStack());
        assertTrue(simulator.stepBackwards());
        assertFalse(simulator.stepBackwards());
        assertEquals(0, simulator.position());
    }

    @Test
    void passesOverLabelsAndLineNumbers() {
        LabelNode start = new LabelNode();
        MethodSimulator simulator = new MethodSimulator(null, staticMethod(
            start, new LineNumberNode(3, start), new InsnNode(Opcodes.RETURN)));

        assertEquals(2, simulator.position());
        assertTrue(simulator.stepForwards());
        assertFalse(simulator.stepForwards());
    }

    @Test
    void refusesObsoleteSubroutinesWithoutChangingState() {
        MethodSimulator simulator = new MethodSimulator(null, staticMethod(
            new InsnNode(Opcodes.ICONST_1), new JumpInsnNode(Opcodes.JSR, new LabelNode())));
        simulator.stepForwards();

        assertThrows(SimulationException.class, simulator::stepForwards);
        assertEquals(List.of(1), simulator.getStack());
        assertEquals(1, simulator.steps());
    }
}
