package org.de.rikr.behavioral;

import org.objectweb.asm.tree.*;

import java.util.*;

public class MethodSimulator {
    private final List<ClassNode> classNodes;
    private Stack<Object> stack = new Stack<>();
    private Map<Integer, Stack<Object>> stackHistory = new HashMap<>();
    private Map<Integer, Object> localVariables = new HashMap<>();
    private Map<String, Object> fieldValues = new HashMap<>();
    private ArrayList<AbstractInsnNode> jumpInstructions = new ArrayList<>();
    private final InstructionExecutorFactory instructionExecutorFactory = new InstructionExecutorFactory();
    private int instructionPointer = 0;

    public MethodSimulator(List<ClassNode> classNodes) {
        this.classNodes = classNodes;
    }

    public boolean step(MethodNode method) {
        while (instructionPointer < method.instructions.size()) {
            AbstractInsnNode instruction = method.instructions.get(instructionPointer);

            if (instruction instanceof LabelNode || instruction instanceof LineNumberNode) {
                instructionPointer++;
                continue;
            }

            InstructionExecutor executor = instructionExecutorFactory.createExecutor(classNodes, instruction);
            Object returnValue = executor.execute(instruction, stack, localVariables, fieldValues);

            if (returnValue instanceof Boolean returnValueBoolean) {
                // Exit the simulation loop if return instruction occurred
                instructionPointer = method.instructions.size();
                return !returnValueBoolean;
            } else if (returnValue instanceof JumpInsnNode jumpInsnNode) {
                // Infinite loop detected, break out of simulation
                if (jumpInstructions.contains(jumpInsnNode)) {
                    return false;
                }
                jumpInstructions.add(jumpInsnNode);
                instructionPointer = method.instructions.indexOf(jumpInsnNode.label);
            } else {
                // Continue with sequential execution
                instructionPointer++;
                return true;
            }
        }

        return false;
    }

    public int stepForwards(MethodNode method) {
        if (instructionPointer >= method.instructions.size() || instructionPointer < 0) {
            return -1;
        }

        if (stackHistory.containsKey(instructionPointer + 1)) {
            instructionPointer++;
            stack = stackHistory.get(instructionPointer);

            return instructionPointer;
        }

        AbstractInsnNode instruction = method.instructions.get(instructionPointer);

        if (instruction instanceof LabelNode || instruction instanceof LineNumberNode) {
            instructionPointer++;
            saveStackHistory(instructionPointer);

            return instructionPointer;
        }

        InstructionExecutor executor = instructionExecutorFactory.createExecutor(classNodes, instruction);
        Object returnValue = executor.execute(instruction, stack, localVariables, fieldValues);

        if (returnValue instanceof Boolean) {
            // Exit the simulation loop if return instruction occurred
            instructionPointer = method.instructions.size();
            return 0;
        } else if (returnValue instanceof JumpInsnNode jumpInsnNode) {
            // Infinite loop detected, break out of simulation
            if (jumpInstructions.contains(jumpInsnNode)) {
                return -1;
            }
            jumpInstructions.add(jumpInsnNode);
            instructionPointer = method.instructions.indexOf(jumpInsnNode.label);
        } else {
            // Continue with sequential execution
            instructionPointer++;
            saveStackHistory(instructionPointer);

            return instructionPointer;
        }

        return -1;
    }

    public int stepBackwards(int index) {
        if (index < 0 || !stackHistory.containsKey(index)) {
            return -1;
        }

        stack = stackHistory.get(index);
        instructionPointer = index;

        return index;
    }

    private Stack<Object> getStackClone() {
        Stack<Object> stackClone = new Stack<>();

        for (Object obj : stack) {
            stackClone.push(obj);
        }

        return stackClone;
    }

    private void saveStackHistory(int instructionPointer) {
        if (!stackHistory.containsKey(instructionPointer)) {
            stackHistory.put(instructionPointer, getStackClone());
        }
    }

    public Stack<Object> getStack() {
        return stack;
    }

    public Map<Integer, Object> getLocalVariables() {
        return localVariables;
    }
}

