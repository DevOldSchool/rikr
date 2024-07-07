package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class TypeConversionExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        if (stack.isEmpty()) {
            return null;
        }

        Object value = stack.pop();
        if (value == null) {
            return null;
        }

        switch (instruction.getOpcode()) {
            case Opcodes.I2B:
                stack.push((byte) ((Integer) value).intValue());
                break;
            case Opcodes.I2C:
                stack.push((char) ((Integer) value).intValue());
                break;
            case Opcodes.I2S:
                stack.push((short) ((Integer) value).intValue());
                break;
        }

        return null;
    }
}
