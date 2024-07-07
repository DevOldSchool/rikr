package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class ReturnExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        switch (instruction.getOpcode()) {
            case Opcodes.RETURN:
            case Opcodes.IRETURN:
            case Opcodes.ARETURN:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                return true;
        }

        return false;
    }
}
