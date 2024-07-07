package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class ControlFlowExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        if (stack.isEmpty()) {
            return null;
        }
        
        Object reference;

        switch (instruction.getOpcode()) {
            case Opcodes.IFNONNULL:
                reference = stack.pop();

                if (reference != null) {
                    return instruction;
                }

                break;
            case Opcodes.IFNULL:
                reference = stack.pop();

                if (reference == null) {
                    return instruction;
                }

                break;
            case Opcodes.GOTO:
                return instruction;
        }

        return null;
    }


}
