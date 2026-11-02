package org.de.rikr.behavioral;

import org.de.rikr.behavioral.SimulatedValue.ArrayRef;
import org.de.rikr.behavioral.SimulatedValue.ClassConstant;
import org.de.rikr.behavioral.SimulatedValue.ObjectRef;
import org.de.rikr.behavioral.SimulatedValue.Unknown;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tells whether two methods, usually from two versions of a program, behave the same: run side by side, they
 * hold the same values after every step. Names of classes, fields and methods are ignored, since renaming and
 * obfuscation change them.
 */
public class MethodComparator {
    // A bound on loops whose exit depends on unknown values; methods that agree this far are taken to agree.
    static final int STEP_LIMIT = 5_000;

    /**
     * Returns true if the simulated methods hold the same stack and local variables at each step.
     *
     * @param owner1 the internal name of the class declaring the first method
     * @param owner2 the internal name of the class declaring the second method
     */
    public boolean areBehaviorallyEquivalent(String owner1, MethodNode method1, String owner2, MethodNode method2) {
        MethodSimulator first = new MethodSimulator(owner1, method1);
        MethodSimulator second = new MethodSimulator(owner2, method2);
        for (int step = 0; step < STEP_LIMIT; step++) {
            boolean firstRan = first.advance();
            boolean secondRan = second.advance();
            if (firstRan != secondRan || !sameValues(first.getStack(), second.getStack())
                || !sameLocals(first.getLocalVariables(), second.getLocalVariables())) {
                return false;
            }
            if (!firstRan) {
                return true;
            }
        }
        return true;
    }

    private static boolean sameValues(List<Object> first, List<Object> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!same(first.get(i), second.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameLocals(Map<Integer, Object> first, Map<Integer, Object> second) {
        if (!first.keySet().equals(second.keySet())) {
            return false;
        }
        return first.keySet().stream().allMatch(slot -> same(first.get(slot), second.get(slot)));
    }

    // Values agree when they are equal, or when they have the same shape where only names could differ.
    static boolean same(Object first, Object second) {
        return switch (first) {
            case Unknown unknown when second instanceof Unknown other ->
                unknown.type().getSort() == other.type().getSort();
            case ObjectRef ref when second instanceof ObjectRef other -> (ref.id() == 0) == (other.id() == 0);
            case ArrayRef array when second instanceof ArrayRef other -> Objects.equals(array.length(), other.length())
                && array.type().getElementType().getSort() == other.type().getElementType().getSort();
            case ClassConstant ignored -> second instanceof ClassConstant;
            case null, default -> Objects.equals(first, second);
        };
    }
}
