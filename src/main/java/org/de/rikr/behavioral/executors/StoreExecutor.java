package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Map;
import java.util.Stack;

public class StoreExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        switch (instruction.getOpcode()) {
            case Opcodes.ISTORE:
            case Opcodes.ASTORE:
            case Opcodes.FSTORE:
            case Opcodes.DSTORE:
            case Opcodes.LSTORE:
                if (stack.isEmpty()) {
                    break;
                }

                int varIndex = ((VarInsnNode) instruction).var;
                localVariables.put(varIndex, stack.pop());
                break;
            case Opcodes.AASTORE:
                handleArrayStore(stack, localVariables, Object[].class);
                break;
            case Opcodes.BASTORE:
                handleArrayStore(stack, localVariables, byte[].class);
                break;
            case Opcodes.CASTORE:
                handleArrayStore(stack, localVariables, char[].class);
                break;
            case Opcodes.DASTORE:
                handleArrayStore(stack, localVariables, double[].class);
                break;
            case Opcodes.FASTORE:
                handleArrayStore(stack, localVariables, float[].class);
                break;
            case Opcodes.IASTORE:
                handleArrayStore(stack, localVariables, int[].class);
                break;
            case Opcodes.LASTORE:
                handleArrayStore(stack, localVariables, long[].class);
                break;
            case Opcodes.SASTORE:
                handleArrayStore(stack, localVariables, short[].class);
                break;
        }

        return null;
    }

    private void handleArrayStore(Stack<Object> stack, Map<Integer, Object> localVariables, Class<?> arrayType) {
        if (stack.size() < 3) {
            return;
        }

        Object value = stack.pop();
        Object obj = stack.pop();
        if (obj == null) {
            return;
        }
        int index = (int) obj;
        Object arrayRef = stack.pop();
        if (!arrayType.isInstance(arrayRef)) {
            return;
        }

        if (index < 0 || index >= java.lang.reflect.Array.getLength(arrayRef)) {
            return;
        }

        if (arrayRef instanceof byte[]) {
            if (!(value instanceof Integer)) {
                return;
            }
            ((byte[]) arrayRef)[index] = (byte) (int) value;
        } else if (arrayRef instanceof char[]) {
            if (!(value instanceof Integer)) {
                return;
            }
            ((char[]) arrayRef)[index] = (char) (int) value;
        } else if (arrayRef instanceof short[]) {
            if (!(value instanceof Integer)) {
                return;
            }
            ((short[]) arrayRef)[index] = (short) (int) value;
        } else if (arrayRef instanceof int[]) {
            if (!(value instanceof Integer)) {
                return;
            }
            ((int[]) arrayRef)[index] = (int) value;
        } else if (arrayRef instanceof long[]) {
            if (!(value instanceof Long)) {
                return;
            }
            ((long[]) arrayRef)[index] = (long) value;
        } else if (arrayRef instanceof float[]) {
            if (!(value instanceof Float)) {
                return;
            }
            ((float[]) arrayRef)[index] = (float) value;
        } else if (arrayRef instanceof double[]) {
            if (!(value instanceof Double)) {
                return;
            }
            ((double[]) arrayRef)[index] = (double) value;
        } else if (arrayRef instanceof Object[]) {
            ((Object[]) arrayRef)[index] = value;
        }

        localVariables.put(index, java.lang.reflect.Array.get(arrayRef, index));
    }
}
