package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class StackManipulationExecutor implements InstructionExecutor {

    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        Object top1;
        Object top2;
        Object top3;

        switch (instruction.getOpcode()) {
            case Opcodes.DUP:
                if (stack.isEmpty()) {
                    return null;
                }

                Object top = stack.peek();
                stack.push(top);
                break;
            case Opcodes.POP:
                if (!stack.isEmpty()) {
                    stack.pop();
                }

                break;
            case Opcodes.SWAP:
                if (stack.size() < 2) {
                    return null;
                }

                top1 = stack.pop();
                top2 = stack.pop();
                stack.push(top1);
                stack.push(top2);
                break;
            case Opcodes.DUP_X1:
                if (stack.size() < 2) {
                    return null;
                }

                top1 = stack.pop();
                top2 = stack.pop();
                stack.push(top1);
                stack.push(top2);
                stack.push(top1);

                break;
            case Opcodes.DUP_X2:
                if (stack.size() >= 3) {
                    top1 = stack.pop();
                    top2 = stack.pop();
                    top3 = stack.pop();
                    stack.push(top1);
                    stack.push(top3);
                    stack.push(top2);
                    stack.push(top1);
                }
                break;
            case Opcodes.DUP2:
                if (stack.size() < 2) {
                    return null;
                }

                top1 = stack.pop();
                top2 = stack.pop();
                stack.push(top2);
                stack.push(top1);
                stack.push(top2);
                stack.push(top1);

                break;
            case Opcodes.DUP2_X1:
                if (stack.size() < 3) {
                    return null;
                }

                top1 = stack.pop();
                top2 = stack.pop();
                top3 = stack.pop();
                stack.push(top2);
                stack.push(top1);
                stack.push(top3);
                stack.push(top2);
                stack.push(top1);

                break;
            case Opcodes.DUP2_X2:
                if (stack.size() < 4) {
                    return null;
                }

                top1 = stack.pop();
                top2 = stack.pop();
                top3 = stack.pop();
                Object top4 = stack.pop();
                stack.push(top2);
                stack.push(top1);
                stack.push(top4);
                stack.push(top3);
                stack.push(top2);
                stack.push(top1);

                break;
            case Opcodes.POP2:
                if (stack.size() >= 2) {
                    stack.pop();
                    stack.pop();
                }
                break;
        }
        return null;
    }
}
