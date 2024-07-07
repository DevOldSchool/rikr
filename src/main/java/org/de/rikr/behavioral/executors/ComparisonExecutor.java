package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class ComparisonExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        if (stack.isEmpty()) {
            return null;
        }

        int opcode = instruction.getOpcode();

        switch (opcode) {
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
                if (stack.size() < 2) {
                    return null;
                }

                Object obj1 = stack.pop();
                Object obj2 = stack.pop();

                if (obj1 == null || obj2 == null) {
                    return null;
                }

                int value1 = convertToInt(obj1);
                int value2 = convertToInt(obj2);

                if (evaluateCondition(opcode, value1, value2)) {
                    return instruction;
                }

                break;

            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
                Object obj = stack.pop();
                if (obj == null) {
                    return null;
                }

                int value = convertToInt(obj);
                if (evaluateCondition(opcode, value, 0)) {
                    return instruction;
                }

                break;

            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                if (stack.size() < 2) {
                    return null;
                }

                Object ref1 = stack.pop();
                Object ref2 = stack.pop();

                if (evaluateReferenceCondition(opcode, ref1, ref2)) {
                    return instruction;
                }

                break;
        }

        return null;
    }

    private int convertToInt(Object obj) {
        if (obj instanceof Integer) {
            return (int) obj;
        } else if (obj instanceof Long) {
            return ((Long) obj).intValue();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + obj.getClass());
        }
    }

    private boolean evaluateCondition(int opcode, int value1, int value2) {
        return switch (opcode) {
            case Opcodes.IF_ICMPEQ, Opcodes.IFEQ -> value1 == value2;
            case Opcodes.IF_ICMPNE, Opcodes.IFNE -> value1 != value2;
            case Opcodes.IF_ICMPLT, Opcodes.IFLT -> value1 < value2;
            case Opcodes.IF_ICMPGE, Opcodes.IFGE -> value1 >= value2;
            case Opcodes.IF_ICMPGT, Opcodes.IFGT -> value1 > value2;
            case Opcodes.IF_ICMPLE, Opcodes.IFLE -> value1 <= value2;
            default -> false;
        };
    }

    private boolean evaluateReferenceCondition(int opcode, Object ref1, Object ref2) {
        return switch (opcode) {
            case Opcodes.IF_ACMPEQ -> ref1 == ref2;
            case Opcodes.IF_ACMPNE -> ref1 != ref2;
            default -> false;
        };
    }
}
