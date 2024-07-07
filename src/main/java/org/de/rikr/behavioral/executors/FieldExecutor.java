package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.util.Map;
import java.util.Stack;

public class FieldExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        FieldInsnNode fieldInsn;
        String key;
        Object value;
        String fieldName;
        String fieldOwner;
        Object fieldValue;

        switch (instruction.getOpcode()) {
            case Opcodes.GETFIELD:
                fieldInsn = (FieldInsnNode) instruction;
                fieldName = fieldInsn.name;
                fieldOwner = fieldInsn.owner;

                key = fieldOwner + "." + fieldName;
                fieldValue = fieldValues.get(key);

                stack.push(fieldValue);
                break;
            case Opcodes.PUTFIELD:
                if (stack.size() < 2) {
                    break;
                }

                fieldInsn = (FieldInsnNode) instruction;
                fieldName = fieldInsn.name;
                fieldOwner = fieldInsn.owner;
                value = stack.pop();
                Object objectRef = stack.pop();

                key = fieldOwner + "." + fieldName;
                fieldValues.put(key, value);
                break;
            case Opcodes.GETSTATIC:
                fieldInsn = (FieldInsnNode) instruction;
                key = fieldInsn.owner + "." + fieldInsn.name;

                value = fieldValues.get(key);
                stack.push(value);
                break;
            case Opcodes.PUTSTATIC:
                if (stack.isEmpty()) {
                    break;
                }

                fieldInsn = (FieldInsnNode) instruction;
                value = stack.pop();

                key = fieldInsn.owner + "." + fieldInsn.name;
                fieldValues.put(key, value);
                break;
        }
        return null;
    }
}
