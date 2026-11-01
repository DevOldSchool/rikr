package org.de.rikr.mapping;

import org.objectweb.asm.tree.ClassNode;

import java.util.List;

/**
 * Reading a ranked list of class match candidates before one is offered: which to leave out, and whether the best is
 * a clear winner.
 */
public final class CandidateRanking {
    private CandidateRanking() {
    }

    /**
     * Leave out candidates in a package when the source class is in no package and some candidates are too. An
     * obfuscator moves a program's classes out of their packages, so in an obfuscated JAR a class in no package
     * matches another in no package; the classes still in packages are libraries bundled with it, whose small
     * interfaces otherwise match as well as the program's own.
     */
    public static List<ClassMatch> forSource(ClassNode source, List<ClassMatch> candidates) {
        if (inPackage(source.name) || candidates.stream().allMatch(match -> inPackage(match.classNode().name))) {
            return candidates;
        }
        return candidates.stream().filter(match -> !inPackage(match.classNode().name)).toList();
    }

    /**
     * How many candidates share the best similarity, counted from the top of a list ranked best first. More than
     * one means there is no unique match to offer.
     */
    public static int tiedAtTop(List<ClassMatch> ranked) {
        if (ranked.isEmpty()) {
            return 0;
        }
        int best = ranked.getFirst().similarityPercent();
        int tied = 0;
        while (tied < ranked.size() && ranked.get(tied).similarityPercent() == best) {
            tied++;
        }
        return tied;
    }

    private static boolean inPackage(String internalName) {
        return internalName.indexOf('/') >= 0;
    }
}
