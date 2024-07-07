package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class ArithmeticExecutor implements InstructionExecutor {
    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        if (stack.size() < 2) {
            return null;
        }

        Object value2 = stack.pop();
        Object value1 = stack.pop();

        if (!(value1 instanceof Number num1) || !(value2 instanceof Number num2)) {
            return null;
        }

        switch (instruction.getOpcode()) {
            case Opcodes.IADD:
                stack.push(num1.intValue() + num2.intValue());
                break;
            case Opcodes.ISUB:
                stack.push(num1.intValue() - num2.intValue());
                break;
            case Opcodes.IMUL:
                stack.push(num1.intValue() * num2.intValue());
                break;
            case Opcodes.IDIV:
                stack.push(num1.intValue() / num2.intValue());
                break;
            case Opcodes.IREM:
                stack.push(num1.intValue() % num2.intValue());
                break;
            case Opcodes.IAND:
                stack.push(num1.intValue() & num2.intValue());
                break;
            case Opcodes.IOR:
                stack.push(num1.intValue() | num2.intValue());
                break;
            case Opcodes.IXOR:
                stack.push(num1.intValue() ^ num2.intValue());
                break;
            case Opcodes.LADD:
                stack.push(num1.longValue() + num2.longValue());
                break;
            case Opcodes.LSUB:
                stack.push(num1.longValue() - num2.longValue());
                break;
            case Opcodes.LMUL:
                stack.push(num1.longValue() * num2.longValue());
                break;
            case Opcodes.LDIV:
                stack.push(num1.longValue() / num2.longValue());
                break;
            case Opcodes.LREM:
                stack.push(num1.longValue() % num2.longValue());
                break;
            case Opcodes.LAND:
                stack.push(num1.longValue() & num2.longValue());
                break;
            case Opcodes.LOR:
                stack.push(num1.longValue() | num2.longValue());
                break;
            case Opcodes.LXOR:
                stack.push(num1.longValue() ^ num2.longValue());
                break;
            case Opcodes.DADD:
                stack.push(num1.doubleValue() + num2.doubleValue());
                break;
            case Opcodes.DSUB:
                stack.push(num1.doubleValue() - num2.doubleValue());
                break;
            case Opcodes.DMUL:
                stack.push(num1.doubleValue() * num2.doubleValue());
                break;
            case Opcodes.DDIV:
                stack.push(num1.doubleValue() / num2.doubleValue());
                break;
            case Opcodes.FADD:
                stack.push(num1.floatValue() + num2.floatValue());
                break;
            case Opcodes.FSUB:
                stack.push(num1.floatValue() - num2.floatValue());
                break;
            case Opcodes.FMUL:
                stack.push(num1.floatValue() * num2.floatValue());
                break;
            case Opcodes.FDIV:
                stack.push(num1.floatValue() / num2.floatValue());
                break;
            case Opcodes.ISHL:
                stack.push((Integer) value1 << (Integer) value2);
                break;
            case Opcodes.ISHR:
                stack.push((Integer) value1 >> (Integer) value2);
                break;
            case Opcodes.IUSHR:
                stack.push((Integer) value1 >>> (Integer) value2);
                break;
        }

        return null;
    }
}
