package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class ArrayExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        int opcode = instruction.getOpcode();

        switch (opcode) {
            case Opcodes.ARRAYLENGTH:
                if (stack.isEmpty()) {
                    return null;
                }

                Object arrayRef = stack.pop();
                if (arrayRef == null) {
                    return null;
                }

                stack.push(java.lang.reflect.Array.getLength(arrayRef));

                break;
        }

        return null;
    }


}
