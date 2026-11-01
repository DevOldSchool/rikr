package org.de.rikr.mapping;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandidateRankingTest {

    @Test
    void leavesOutPackagedCandidatesForAClassInNoPackage() {
        ClassMatch obfuscated = match("af", 100);
        ClassMatch library = match("org/lib/Choice", 100);
        ClassMatch other = match("cy", 80);

        List<ClassMatch> kept = CandidateRanking.forSource(named("bm"), List.of(obfuscated, library, other));

        assertEquals(List.of(obfuscated, other), kept);
    }

    @Test
    void keepsEveryCandidateForAClassInAPackage() {
        List<ClassMatch> candidates = List.of(match("af", 100), match("org/lib/Choice", 90));

        assertEquals(candidates, CandidateRanking.forSource(named("app/Player"), candidates));
    }

    @Test
    void keepsPackagedCandidatesWhenNoneIsInNoPackage() {
        List<ClassMatch> candidates = List.of(match("app/Player", 100), match("app/Npc", 90));

        assertEquals(candidates, CandidateRanking.forSource(named("bm"), candidates));
    }

    @Test
    void countsTheCandidatesTiedForBest() {
        assertEquals(0, CandidateRanking.tiedAtTop(List.of()));
        assertEquals(1, CandidateRanking.tiedAtTop(List.of(match("a", 100), match("b", 80))));
        assertEquals(3, CandidateRanking.tiedAtTop(List.of(match("a", 90), match("b", 90), match("c", 90))));
        assertEquals(2, CandidateRanking.tiedAtTop(List.of(match("a", 100), match("b", 100), match("c", 50))));
    }

    private static ClassMatch match(String name, int percent) {
        return new ClassMatch(named(name), percent);
    }

    private static ClassNode named(String name) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        return classNode;
    }
}
