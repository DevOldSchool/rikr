package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Map;
import java.util.Stack;

public class LoadExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        switch (instruction.getOpcode()) {
            case Opcodes.ILOAD:
            case Opcodes.ALOAD:
            case Opcodes.DLOAD:
            case Opcodes.FLOAD:
                int varIndex = ((VarInsnNode) instruction).var;

                // Intentionally left out 'this' reference for easier stack comparison
                if (varIndex != 0) {
                    stack.push(localVariables.get(varIndex));
                }
                break;
            case Opcodes.AALOAD:
                if (handleArrayLoad(stack, Object[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.BALOAD:
                if (handleArrayLoad(stack, byte[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.CALOAD:
                if (handleArrayLoad(stack, char[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.DALOAD:
                if (handleArrayLoad(stack, double[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.FALOAD:
                if (handleArrayLoad(stack, float[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.IALOAD:
                if (handleArrayLoad(stack, int[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.LALOAD:
                if (handleArrayLoad(stack, long[].class) == null) {
                    return null;
                }

                break;
            case Opcodes.SALOAD:
                if (handleArrayLoad(stack, short[].class) == null) {
                    return null;
                }

                break;
        }

        return null;
    }

    private Object handleArrayLoad(Stack<Object> stack, Class<?> arrayType) {
        if (stack.size() < 2) {
            return null;
        }

        Object obj = stack.pop();
        if (obj == null) {
            return null;
        }
        int index = (int) obj;

        Object arrayRef = stack.pop();
        if (!arrayType.isInstance(arrayRef)) {
            return null;
        }

        if (index < 0 || index >= java.lang.reflect.Array.getLength(arrayRef)) {
            return null;
        }

        Object value = java.lang.reflect.Array.get(arrayRef, index);
        if (arrayRef instanceof byte[] || arrayRef instanceof boolean[]) {
            stack.push((int) (byte) value);
        } else if (arrayRef instanceof char[]) {
            stack.push((int) (char) value);
        } else if (arrayRef instanceof short[]) {
            stack.push((int) (short) value);
        } else {
            stack.push(value);
        }

        return value;
    }
}
