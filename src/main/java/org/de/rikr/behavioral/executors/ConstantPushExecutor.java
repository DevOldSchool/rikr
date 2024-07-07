package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

import java.util.Map;
import java.util.Stack;

public class ConstantPushExecutor implements InstructionExecutor {

    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        switch (instruction.getOpcode()) {
            case Opcodes.ICONST_M1:
                stack.push(-1);
                break;
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                stack.push(instruction.getOpcode() - Opcodes.ICONST_0);
                break;
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                stack.push((float) (instruction.getOpcode() - Opcodes.FCONST_0));
                break;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                stack.push((long) (instruction.getOpcode() - Opcodes.LCONST_0));
                break;
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                stack.push(((org.objectweb.asm.tree.IntInsnNode) instruction).operand);
                break;
            case Opcodes.ACONST_NULL:
                stack.push(null);
                break;
            case Opcodes.LDC:
                stack.push(((LdcInsnNode) instruction).cst);
                break;
        }

        return null;
    }
}
