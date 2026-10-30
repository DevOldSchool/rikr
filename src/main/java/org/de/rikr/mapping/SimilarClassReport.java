package org.de.rikr.mapping;

import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Classes in other JARs that look like one class, each with the methods that behave the same in both.
 *
 * @param skippedComparisons method pairs that could not be simulated, so their behaviour is unknown
 */
public record SimilarClassReport(List<Match> matches, int skippedComparisons) {
    public SimilarClassReport {
        matches = List.copyOf(matches);
    }

    public record Match(String jar, ClassMatch match, List<MethodPair> equivalentMethods) {
        public Match {
            equivalentMethods = List.copyOf(equivalentMethods);
        }
    }

    public record MethodPair(MethodNode source, MethodNode target) {
    }
}
