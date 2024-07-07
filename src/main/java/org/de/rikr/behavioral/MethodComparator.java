package org.de.rikr.behavioral;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MethodComparator {

    /**
     * Returns true if the simulated methods produce the same stack and local variables.
     * Comparison is checked at each instruction step.
     *
     * @param classNodes1 List of class nodes to be used to instantiation
     * @param methodNode1 Method to compare from
     * @param classNodes2 List of class nodes to be used to instantiation
     * @param methodNode2 Method to compare to
     * @return True if the methods match
     */
    public boolean areBehaviorallyEquivalent(List<ClassNode> classNodes1, MethodNode methodNode1, List<ClassNode> classNodes2, MethodNode methodNode2) {
        MethodSimulator simulator1 = new MethodSimulator(classNodes1);
        MethodSimulator simulator2 = new MethodSimulator(classNodes2);

        boolean areEquivalent = false;
        boolean comparing = true;

        while (comparing) {
            boolean step1 = simulator1.step(methodNode1);
            boolean step2 = simulator2.step(methodNode2);

            Stack<Object> stack1 = simulator1.getStack();
            Stack<Object> stack2 = simulator2.getStack();

            Map<Integer, Object> locals1 = simulator1.getLocalVariables();
            Map<Integer, Object> locals2 = simulator2.getLocalVariables();

            areEquivalent = stack1.equals(stack2) && locals1.equals(locals2);

            if (!areEquivalent || (!step1 && !step2)) {
                comparing = false;
            }
        }

        return areEquivalent;
    }
}
