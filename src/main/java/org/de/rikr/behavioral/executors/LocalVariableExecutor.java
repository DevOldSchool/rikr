package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;

import java.util.Map;
import java.util.Stack;

public class LocalVariableExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        switch (instruction.getOpcode()) {
            case Opcodes.IINC:
                IincInsnNode iincInsn = (IincInsnNode) instruction;
                int varIndex = iincInsn.var;
                int increment = iincInsn.incr;

                Object currentValue = localVariables.get(varIndex);
                if (currentValue instanceof Integer) {
                    localVariables.put(varIndex, (Integer) currentValue + increment);
                }
                
                break;
        }
        return null;
    }
}