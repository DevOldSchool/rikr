package org.de.rikr.behavioral;

import org.objectweb.asm.tree.*;

import java.util.*;

public class MethodSimulator {
    private final List<ClassNode> classNodes;
    private final Stack<Object> stack = new Stack<>();
    private final Map<Integer, Object> localVariables = new HashMap<>();
    private final Map<String, Object> fieldValues = new HashMap<>();
    private final ArrayList<AbstractInsnNode> jumpInstructions = new ArrayList<>();
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

    public Stack<Object> getStack() {
        return stack;
    }

    public Map<Integer, Object> getLocalVariables() {
        return localVariables;
    }
}

