package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Map;
import java.util.Stack;

public class DefaultExecutor implements InstructionExecutor {

    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        return null;
    }
}
