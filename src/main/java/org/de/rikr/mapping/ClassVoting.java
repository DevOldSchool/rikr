package org.de.rikr.mapping;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds more class matches from the members already matched. Every matched field and method says which classes
 * its types are on both sides: a field of type {@code Lao;} matched to one of type {@code Lclass12;} is a vote
 * for {@code ao} and {@code class12}, and so are their parameter and return types, and the superclasses of
 * matched classes. A class is matched when one candidate clearly has the most votes and it clearly has the most
 * votes for that candidate back. Matching members again with the new classes finds more votes, so this repeats
 * until nothing new is found.
 * <p>
 * Structural matching compares a class's shape, which changes when a deobfuscator removes unused parameters or
 * multiplications; votes come from what the members are, so they still work across such changes.
 */
public class ClassVoting {
    /** The fewest votes that match a class. */
    static final int MIN_VOTES = 2;
    /** How many times the votes for the best candidate must outnumber those for the runner-up. */
    static final int DOMINANCE = 2;
    private static final int SUPERCLASS_VOTES = 3;
    private static final int MAX_ROUNDS = 8;

    private final MemberMatcher memberMatcher = new MemberMatcher();

    /**
     * A class matched by votes.
     *
     * @param votes       votes for this target
     * @param runnerUp    votes for the next best target
     */
    public record VotedMatch(String source, String target, int votes, int runnerUp) {
        /** The share of the two leading candidates' votes that went to this one, as a percentage. */
        public int confidencePercent() {
            return (int) Math.round(100.0 * votes / (votes + runnerUp));
        }
    }

    /**
     * @param classMapping source class name to target class name, as matched so far
     * @return the classes matched by votes, in the order they were found; the given mapping is not changed
     */
    public List<VotedMatch> match(List<ClassNode> sourceClasses, List<ClassNode> targetClasses,
        Map<String, String> classMapping) {
        Map<String, ClassNode> sources = byName(sourceClasses);
        Map<String, ClassNode> targets = byName(targetClasses);
        Map<String, String> mapping = new LinkedHashMap<>(classMapping);
        Set<String> mappedTargets = new HashSet<>(mapping.values());
        List<VotedMatch> found = new ArrayList<>();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            MemberMatches members = memberMatcher.match(sourceClasses, targetClasses, mapping);
            Map<String, Map<String, Integer>> votes = new HashMap<>();
            for (MemberMatch match : members.fields()) {
                vote(votes, Type.getType(match.source().descriptor()), Type.getType(match.target().descriptor()), 1,
                    sources, targets);
            }
            for (MemberMatch match : members.methods()) {
                Type source = Type.getMethodType(match.source().descriptor());
                Type target = Type.getMethodType(match.target().descriptor());
                vote(votes, source.getReturnType(), target.getReturnType(), 1, sources, targets);
                Type[] sourceArguments = source.getArgumentTypes();
                Type[] targetArguments = target.getArgumentTypes();
                // An unused last parameter may be on one side only; the others still line up from the start.
                for (int index = 0; index < Math.min(sourceArguments.length, targetArguments.length); index++) {
                    vote(votes, sourceArguments[index], targetArguments[index], 1, sources, targets);
                }
            }
            for (Map.Entry<String, String> pair : mapping.entrySet()) {
                ClassNode source = sources.get(pair.getKey());
                ClassNode target = targets.get(pair.getValue());
                if (source != null && target != null && source.superName != null && target.superName != null) {
                    vote(votes, Type.getObjectType(source.superName), Type.getObjectType(target.superName),
                        SUPERCLASS_VOTES, sources, targets);
                }
            }

            List<VotedMatch> accepted = decide(votes, mapping, mappedTargets, sources, targets);
            if (accepted.isEmpty()) {
                break;
            }
            for (VotedMatch match : accepted) {
                mapping.put(match.source(), match.target());
                mappedTargets.add(match.target());
            }
            found.addAll(accepted);
        }
        return found;
    }

    private static void vote(Map<String, Map<String, Integer>> votes, Type source, Type target, int weight,
        Map<String, ClassNode> sources, Map<String, ClassNode> targets) {
        if (source.getSort() == Type.ARRAY && target.getSort() == Type.ARRAY) {
            if (source.getDimensions() != target.getDimensions()) {
                return;
            }
            source = source.getElementType();
            target = target.getElementType();
        }
        if (source.getSort() != Type.OBJECT || target.getSort() != Type.OBJECT) {
            return;
        }
        String sourceName = source.getInternalName();
        String targetName = target.getInternalName();
        if (!sources.containsKey(sourceName) || !targets.containsKey(targetName)) {
            return;
        }
        votes.computeIfAbsent(sourceName, ignored -> new HashMap<>()).merge(targetName, weight, Integer::sum);
    }

    // Matches each unmatched source class whose leading candidate is clear both ways and still free.
    private static List<VotedMatch> decide(Map<String, Map<String, Integer>> votes, Map<String, String> mapping,
        Set<String> mappedTargets, Map<String, ClassNode> sources, Map<String, ClassNode> targets) {
        Map<String, Map<String, Integer>> byTarget = new HashMap<>();
        votes.forEach((source, counts) -> counts.forEach((target, count) ->
            byTarget.computeIfAbsent(target, ignored -> new HashMap<>()).put(source, count)));
        List<VotedMatch> accepted = new ArrayList<>();
        List<String> order = new ArrayList<>(votes.keySet());
        order.sort(Comparator.naturalOrder());
        for (String source : order) {
            if (mapping.containsKey(source)) {
                continue;
            }
            Leader leader = Leader.of(votes.get(source));
            if (leader == null || mappedTargets.contains(leader.name())) {
                continue;
            }
            Leader back = Leader.of(byTarget.get(leader.name()));
            if (back == null || !back.name().equals(source)) {
                continue;
            }
            boolean interfaces = Modifier.isInterface(sources.get(source).access)
                == Modifier.isInterface(targets.get(leader.name()).access);
            if (interfaces) {
                accepted.add(new VotedMatch(source, leader.name(), leader.votes(), leader.runnerUp()));
            }
        }
        return accepted;
    }

    private static Map<String, ClassNode> byName(List<ClassNode> classes) {
        Map<String, ClassNode> byName = new HashMap<>();
        for (ClassNode classNode : classes) {
            byName.put(classNode.name, classNode);
        }
        return byName;
    }

    // The candidate with the most votes, when it has enough and clearly more than the next.
    private record Leader(String name, int votes, int runnerUp) {
        static Leader of(Map<String, Integer> counts) {
            if (counts == null || counts.isEmpty()) {
                return null;
            }
            List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
            ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
            int best = ranked.getFirst().getValue();
            int second = ranked.size() > 1 ? ranked.get(1).getValue() : 0;
            // A tie for first makes second equal to best, so it fails here too.
            if (best < MIN_VOTES || best < DOMINANCE * second) {
                return null;
            }
            return new Leader(ranked.getFirst().getKey(), best, second);
        }
    }
}
