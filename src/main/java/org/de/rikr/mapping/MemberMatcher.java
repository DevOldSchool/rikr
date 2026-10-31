package org.de.rikr.mapping;

import org.de.rikr.behavioral.MethodComparator;
import org.de.rikr.mapping.MemberMatch.MemberRef;
import org.de.rikr.mapping.MemberMatch.Reason;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Matches the fields and methods of one JAR to those of another, once some of their classes have been matched.
 * <p>
 * Members are compared only with members of the same kind and the same type, where a type is written in the names
 * of the target JAR: a class of the source JAR becomes the class it was matched to, and an unmatched class of
 * either JAR becomes "?". Instance members are compared within matched classes. Static members are compared
 * across the whole JAR, because obfuscators such as the one used for Old School RuneScape move them between
 * classes in every release.
 * <p>
 * Methods are compared by what their code does: its instructions, strings, small constants, and the calls,
 * fields and types it uses. Large integer constants are left out, since obfuscators that multiply fields by a
 * constant pick new constants in every release. Fields are then compared by which matched methods read and write
 * them. A member is matched only when one candidate is clearly the best for it and it is clearly the best for that
 * candidate; ties are left unmatched.
 */
public class MemberMatcher {
    /** The lowest similarity that matches a member that has several candidates. */
    static final int MIN_SIMILAR_PERCENT = 60;
    /** The lowest similarity that matches a member whose only candidate has the same kind and type. */
    static final int MIN_ONLY_CANDIDATE_PERCENT = 40;
    /** Confidence of a match that rests only on being the one candidate, with nothing to compare. */
    static final int NO_EVIDENCE_PERCENT = 50;
    // Larger groups of same-typed members are left unmatched instead of being compared pair by pair.
    static final long MAX_PAIRS_PER_GROUP = 250_000;
    // Strings rarely change between versions and say a lot about what a method is for.
    private static final int STRING_WEIGHT = 4;
    private static final int REFERENCE_WEIGHT = 2;
    private static final int CALLER_WEIGHT = 3;
    private static final int ROUNDS = 2;
    private static final int NO_EVIDENCE = -1;

    private final MethodComparator methodComparator = new MethodComparator();

    /**
     * @param classMapping source class name to the name of the target class it was matched to
     */
    public MemberMatches match(
        List<ClassNode> sourceClasses,
        List<ClassNode> targetClasses,
        Map<String, String> classMapping
    ) {
        Set<String> matchedTargets = new HashSet<>(classMapping.values());
        Side source = new Side(sourceClasses, classMapping::get);
        Side target = new Side(targetClasses, name -> matchedTargets.contains(name) ? name : null);

        Map<String, Group<Member<MethodNode>>> methodGroups = new LinkedHashMap<>();
        Map<String, Group<Member<FieldNode>>> fieldGroups = new LinkedHashMap<>();
        int sourceMethods = 0;
        int sourceFields = 0;
        for (Side side : List.of(source, target)) {
            boolean isSource = side == source;
            for (ClassNode owner : side.classes) {
                boolean matchedClass = side.translate.apply(owner.name) != null;
                for (MethodNode method : owner.methods) {
                    boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0 && !"<clinit>".equals(method.name);
                    if (!isStatic && !matchedClass) {
                        continue;
                    }
                    // Constructors and static initializers keep their names, so they only match their own kind.
                    String special = method.name.startsWith("<") ? method.name : "";
                    String key = (isStatic ? "static " : side.type(owner.name) + " ") + special
                        + side.methodKey(method.desc);
                    methodGroups.computeIfAbsent(key, ignored -> new Group<>())
                        .side(isSource).add(new Member<>(owner, method));
                    if (isSource) {
                        sourceMethods++;
                    }
                }
                for (FieldNode field : owner.fields) {
                    boolean isStatic = (field.access & Opcodes.ACC_STATIC) != 0;
                    if (!isStatic && !matchedClass) {
                        continue;
                    }
                    String key = (isStatic ? "static " : side.type(owner.name) + " ") + side.descriptor(field.desc);
                    fieldGroups.computeIfAbsent(key, ignored -> new Group<>())
                        .side(isSource).add(new Member<>(owner, field));
                    if (isSource) {
                        sourceFields++;
                    }
                }
            }
        }

        Map<MethodNode, Map<String, Integer>> code = new IdentityHashMap<>();
        for (Group<Member<MethodNode>> group : methodGroups.values()) {
            group.sources.forEach(member -> code.put(member.node(), source.features(member.owner(), member.node())));
            group.targets.forEach(member -> code.put(member.node(), target.features(member.owner(), member.node())));
        }
        // Code alone can't tell apart the unused copies of a method that obfuscators add, nor a method from one
        // that does the same to another field. Each round adds what the last one matched: which matched methods
        // call a method and which matched fields it uses, and which matched methods use a field.
        List<Pair<MethodNode>> methodPairs = matchMethods(methodGroups, code);
        List<Pair<FieldNode>> fieldPairs = List.of();
        for (int round = 0; round < ROUNDS; round++) {
            Ids<MethodNode> methodIds = Ids.of(methodPairs);
            fieldPairs = matchFields(fieldGroups, source.fieldUses(methodIds.source()),
                target.fieldUses(methodIds.target()));
            Ids<FieldNode> fieldIds = Ids.of(fieldPairs);
            Map<MethodNode, Map<String, Integer>> features = new IdentityHashMap<>();
            for (Map<MethodNode, Map<String, Integer>> evidence : List.of(code,
                source.callers(methodIds.source()), target.callers(methodIds.target()),
                source.fieldReferences(fieldIds.source()), target.fieldReferences(fieldIds.target()))) {
                evidence.forEach((method, counts) -> {
                    Map<String, Integer> combined = features.computeIfAbsent(method, ignored -> new HashMap<>());
                    counts.forEach((feature, count) -> combined.merge(feature, count, Integer::sum));
                });
            }
            methodPairs = matchMethods(methodGroups, features);
        }
        Ids<MethodNode> methodIds = Ids.of(methodPairs);
        fieldPairs = matchFields(fieldGroups, source.fieldUses(methodIds.source()), target.fieldUses(methodIds.target()));

        return new MemberMatches(
            methodPairs.stream().map(pair -> pair.toMatch(method -> method.name, method -> method.desc))
                .sorted(Comparator.comparing(match -> match.source().toString())).toList(),
            fieldPairs.stream().map(pair -> pair.toMatch(field -> field.name, field -> field.desc))
                .sorted(Comparator.comparing(match -> match.source().toString())).toList(),
            sourceMethods,
            sourceFields
        );
    }

    private static List<Pair<FieldNode>> matchFields(
        Map<String, Group<Member<FieldNode>>> groups,
        Map<FieldNode, Map<String, Integer>> sourceUses,
        Map<FieldNode, Map<String, Integer>> targetUses
    ) {
        List<Pair<FieldNode>> pairs = new ArrayList<>();
        for (Group<Member<FieldNode>> group : groups.values()) {
            pairs.addAll(assign(group, (left, right) -> new Score(
                dice(sourceUses.getOrDefault(left.node(), Map.of()), targetUses.getOrDefault(right.node(), Map.of())),
                Reason.SIMILAR_USE)));
        }
        return pairs;
    }

    private List<Pair<MethodNode>> matchMethods(
        Map<String, Group<Member<MethodNode>>> groups,
        Map<MethodNode, Map<String, Integer>> features
    ) {
        List<Pair<MethodNode>> pairs = new ArrayList<>();
        for (Group<Member<MethodNode>> group : groups.values()) {
            pairs.addAll(assign(group, (left, right) ->
                scoreMethods(left, right, features.get(left.node()), features.get(right.node()))));
        }
        return pairs;
    }

    private Score scoreMethods(
        Member<MethodNode> left,
        Member<MethodNode> right,
        Map<String, Integer> leftFeatures,
        Map<String, Integer> rightFeatures
    ) {
        int score = dice(leftFeatures, rightFeatures);
        if (score < 100) {
            return new Score(score, Reason.SIMILAR_CODE);
        }
        // The features leave out names and large constants, so running both tells apart methods they don't.
        try {
            if (methodComparator.areBehaviorallyEquivalent(left.owner().name, left.node(), right.owner().name,
                right.node())) {
                return new Score(100, Reason.SAME_BEHAVIOUR);
            }
        } catch (RuntimeException ignored) {
            // Code the simulator can't run is judged by its features alone.
        }
        return new Score(99, Reason.SIMILAR_CODE);
    }

    /**
     * Pairs members of the group one to one, best score first. A member whose best score is shared by two
     * candidates is left unmatched, and so are those candidates, rather than settled by a guess.
     */
    private static <N> List<Pair<N>> assign(Group<Member<N>> group, Scorer<N> scorer) {
        if (group.sources.isEmpty() || group.targets.isEmpty()
            || (long) group.sources.size() * group.targets.size() > MAX_PAIRS_PER_GROUP) {
            return List.of();
        }
        boolean onlyCandidate = group.sources.size() == 1 && group.targets.size() == 1;
        List<Candidate<N>> candidates = new ArrayList<>();
        for (Member<N> source : group.sources) {
            for (Member<N> target : group.targets) {
                Score score = scorer.score(source, target);
                if (score.percent() == NO_EVIDENCE) {
                    if (onlyCandidate) {
                        candidates.add(new Candidate<>(source, target, NO_EVIDENCE_PERCENT, Reason.ONLY_CANDIDATE));
                    }
                } else if (score.percent() >= MIN_SIMILAR_PERCENT) {
                    candidates.add(new Candidate<>(source, target, score.percent(), score.reason()));
                } else if (onlyCandidate && score.percent() >= MIN_ONLY_CANDIDATE_PERCENT) {
                    candidates.add(new Candidate<>(source, target, score.percent(), Reason.ONLY_CANDIDATE));
                }
            }
        }
        candidates.sort(Comparator.comparingInt((Candidate<N> candidate) -> candidate.percent()).reversed());

        Set<Member<N>> used = new HashSet<>();
        List<Pair<N>> pairs = new ArrayList<>();
        int start = 0;
        while (start < candidates.size()) {
            int end = start;
            while (end < candidates.size() && candidates.get(end).percent() == candidates.get(start).percent()) {
                end++;
            }
            List<Candidate<N>> level = candidates.subList(start, end).stream()
                .filter(candidate -> !used.contains(candidate.source()) && !used.contains(candidate.target()))
                .toList();
            Map<Member<N>, Integer> counts = new HashMap<>();
            for (Candidate<N> candidate : level) {
                counts.merge(candidate.source(), 1, Integer::sum);
                counts.merge(candidate.target(), 1, Integer::sum);
            }
            for (Candidate<N> candidate : level) {
                if (counts.get(candidate.source()) == 1 && counts.get(candidate.target()) == 1) {
                    pairs.add(new Pair<>(candidate.source(), candidate.target(), candidate.percent(),
                        candidate.reason()));
                }
            }
            // Members tied at this score are used up too, so a weaker candidate can't claim them instead.
            for (Candidate<N> candidate : level) {
                used.add(candidate.source());
                used.add(candidate.target());
            }
            start = end;
        }
        return pairs;
    }

    /**
     * How much two multisets overlap, from 0 to 100, or {@link #NO_EVIDENCE} when either is empty.
     */
    static int dice(Map<String, Integer> left, Map<String, Integer> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return NO_EVIDENCE;
        }
        int overlap = 0;
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            overlap += Math.min(entry.getValue(), right.getOrDefault(entry.getKey(), 0));
        }
        int total = left.values().stream().mapToInt(Integer::intValue).sum()
            + right.values().stream().mapToInt(Integer::intValue).sum();
        return (int) Math.round(200.0 * overlap / total);
    }

    /**
     * One JAR, with its class names written so they can be compared with the other JAR's.
     */
    private static final class Side {
        private final List<ClassNode> classes;
        private final Map<String, ClassNode> byName = new HashMap<>();
        private final Function<String, String> translate;
        private final Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return type(internalName);
            }
        };

        /**
         * @param translate a class of this JAR to the name it is compared by, or null when it wasn't matched
         */
        Side(List<ClassNode> classes, Function<String, String> translate) {
            this.classes = classes;
            this.translate = translate;
            for (ClassNode classNode : classes) {
                byName.put(classNode.name, classNode);
            }
        }

        // Library classes keep their names; classes of the JAR are known only through the class mapping.
        String type(String internalName) {
            if (!byName.containsKey(internalName)) {
                return internalName;
            }
            String translated = translate.apply(internalName);
            return translated == null ? "?" : translated;
        }

        String descriptor(String descriptor) {
            return remapper.mapDesc(descriptor);
        }

        String methodDescriptor(String descriptor) {
            return remapper.mapMethodDesc(descriptor);
        }

        /**
         * The method descriptor written for comparison, with a last parameter of int, short or byte written as "~".
         * Obfuscators that add an unused parameter to check callers choose its type anew in every release.
         */
        String methodKey(String descriptor) {
            String mapped = methodDescriptor(descriptor);
            int close = mapped.indexOf(')');
            if (close > 1 && "IBS".indexOf(mapped.charAt(close - 1)) >= 0) {
                char before = mapped.charAt(close - 2);
                // A trailing I of "[I" or of a class name such as "Lfoo/I;" is not an int parameter.
                boolean partOfType = before == '[' || mapped.lastIndexOf('L', close - 1) > mapped.lastIndexOf(';', close - 1);
                if (!partOfType) {
                    return mapped.substring(0, close - 1) + "~" + mapped.substring(close);
                }
            }
            return mapped;
        }

        // Members of library classes are compared by name too, since those names don't change.
        String member(String owner, String name) {
            return byName.containsKey(owner) ? type(owner) : owner + "." + name;
        }

        Map<String, Integer> features(ClassNode owner, MethodNode method) {
            Map<String, Integer> features = new HashMap<>();
            if ((method.access & Opcodes.ACC_STATIC) != 0 && method.instructions.size() > 0) {
                // A static method that stayed in its class is likelier the same; one that moved isn't ruled out.
                add(features, "owner " + type(owner.name), REFERENCE_WEIGHT);
            }
            for (AbstractInsnNode instruction : method.instructions) {
                int opcode = instruction.getOpcode();
                if (opcode < 0) {
                    continue;
                }
                // Control flow obfuscation adds and removes jumps, so they say nothing about what the code does.
                if (opcode != Opcodes.GOTO) {
                    add(features, "op " + opcode, 1);
                }
                switch (instruction) {
                    case LdcInsnNode ldc -> {
                        switch (ldc.cst) {
                            case String string -> add(features, "string " + string, STRING_WEIGHT);
                            case Type type -> add(features, "class " + descriptor(type.getDescriptor()), 1);
                            case Float value -> add(features, "number " + value, 1);
                            case Double value -> add(features, "number " + value, 1);
                            default -> {
                                // Ints and longs are left out: obfuscators change multipliers in every release.
                            }
                        }
                    }
                    case IntInsnNode value -> add(features, "int " + opcode + " " + value.operand, 1);
                    case MethodInsnNode call -> add(features, "call " + member(call.owner, call.name)
                        + methodDescriptor(call.desc), REFERENCE_WEIGHT);
                    case FieldInsnNode access -> add(features, "field " + opcode + " "
                        + member(access.owner, access.name) + ":" + descriptor(access.desc), REFERENCE_WEIGHT);
                    case TypeInsnNode type -> add(features, "type " + opcode + " "
                        + (type.desc.startsWith("[") ? descriptor(type.desc) : type(type.desc)), 1);
                    case MultiANewArrayInsnNode array -> add(features, "type " + descriptor(array.desc), 1);
                    case InvokeDynamicInsnNode dynamic -> add(features, "dynamic " + methodDescriptor(dynamic.desc), 1);
                    default -> {
                    }
                }
            }
            return features;
        }

        /**
         * For each method of the JAR, which matched methods call it, counted, as features to compare it by.
         */
        Map<MethodNode, Map<String, Integer>> callers(Map<MethodNode, Integer> methodIds) {
            Map<MethodNode, Map<String, Integer>> callers = new IdentityHashMap<>();
            for (ClassNode owner : classes) {
                for (MethodNode method : owner.methods) {
                    Integer id = methodIds.get(method);
                    if (id == null) {
                        continue;
                    }
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call) {
                            MethodNode called = resolveMethod(call);
                            if (called != null) {
                                add(callers.computeIfAbsent(called, ignored -> new HashMap<>()), "caller " + id,
                                    CALLER_WEIGHT);
                            }
                        }
                    }
                }
            }
            return callers;
        }

        /**
         * For each method of the JAR, which matched fields it reads and writes, counted.
         */
        Map<MethodNode, Map<String, Integer>> fieldReferences(Map<FieldNode, Integer> fieldIds) {
            Map<MethodNode, Map<String, Integer>> references = new IdentityHashMap<>();
            for (ClassNode owner : classes) {
                for (MethodNode method : owner.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode access) {
                            Integer id = fieldIds.get(resolveField(access));
                            if (id != null) {
                                add(references.computeIfAbsent(method, ignored -> new HashMap<>()),
                                    "uses field " + id + " " + access.getOpcode(), REFERENCE_WEIGHT);
                            }
                        }
                    }
                }
            }
            return references;
        }

        /**
         * For each field of the JAR, how it is accessed by the methods that were matched: the method's id and the
         * opcode, counted. A field with a constant value has that too.
         */
        Map<FieldNode, Map<String, Integer>> fieldUses(Map<MethodNode, Integer> methodIds) {
            Map<FieldNode, Map<String, Integer>> uses = new IdentityHashMap<>();
            for (ClassNode owner : classes) {
                for (FieldNode field : owner.fields) {
                    if (field.value != null) {
                        add(uses.computeIfAbsent(field, ignored -> new HashMap<>()), "value " + field.value, 1);
                    }
                }
                for (MethodNode method : owner.methods) {
                    Integer id = methodIds.get(method);
                    if (id == null) {
                        continue;
                    }
                    // Constructors and static initializers set fields in the order the source declares them,
                    // which survives renaming and the reordering of declarations, so it tells apart fields that
                    // are otherwise used alike. Elsewhere, control flow obfuscation reorders the code.
                    List<FieldNode> initialized = new ArrayList<>();
                    boolean initializer = method.name.startsWith("<");
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode access) {
                            FieldNode field = resolveField(access);
                            if (field != null) {
                                Map<String, Integer> fieldUses = uses.computeIfAbsent(field, ignored -> new HashMap<>());
                                add(fieldUses, id + " " + access.getOpcode(), 1);
                                add(fieldUses, id + " " + access.getOpcode() + " " + context(access), 1);
                                boolean write = access.getOpcode() == Opcodes.PUTFIELD
                                    || access.getOpcode() == Opcodes.PUTSTATIC;
                                if (initializer && write && owner.fields.contains(field)) {
                                    initialized.add(field);
                                }
                            }
                        }
                    }
                    // Counted from both ends, so a field added in a new version shifts only one of the two.
                    for (int index = 0; index < initialized.size(); index++) {
                        Map<String, Integer> fieldUses = uses.get(initialized.get(index));
                        add(fieldUses, id + " sets #" + index, 1);
                        add(fieldUses, id + " sets #-" + (initialized.size() - index), 1);
                    }
                }
            }
            return uses;
        }

        /**
         * Where a written value comes from, or where a read value goes, such as "load parameter 2". Fields used
         * by the same methods in the same ways differ in this, as x and y set from a constructor's parameters do.
         * A multiplication by a constant is passed over, since obfuscators wrap fields in one.
         */
        static String context(FieldInsnNode access) {
            boolean write = access.getOpcode() == Opcodes.PUTFIELD || access.getOpcode() == Opcodes.PUTSTATIC;
            AbstractInsnNode next = step(access, write);
            if (write && next != null && isMultiply(next)) {
                // The multiplier may come before or after the value it multiplies.
                AbstractInsnNode first = step(next, true);
                AbstractInsnNode second = first == null ? null : step(first, true);
                next = isConstant(first) ? second : isConstant(second) ? first : next;
            } else if (!write && isConstant(next)) {
                AbstractInsnNode multiply = step(next, false);
                if (multiply != null && isMultiply(multiply)) {
                    next = step(multiply, false);
                }
            }
            if (next == null) {
                return "none";
            }
            return next instanceof VarInsnNode variable ? next.getOpcode() + " " + variable.var
                : String.valueOf(next.getOpcode());
        }

        private static AbstractInsnNode step(AbstractInsnNode from, boolean backwards) {
            AbstractInsnNode instruction = backwards ? from.getPrevious() : from.getNext();
            while (instruction != null && instruction.getOpcode() < 0) {
                instruction = backwards ? instruction.getPrevious() : instruction.getNext();
            }
            return instruction;
        }

        private static boolean isMultiply(AbstractInsnNode instruction) {
            return instruction.getOpcode() == Opcodes.IMUL || instruction.getOpcode() == Opcodes.LMUL;
        }

        private static boolean isConstant(AbstractInsnNode instruction) {
            return instruction instanceof LdcInsnNode ldc && (ldc.cst instanceof Integer || ldc.cst instanceof Long);
        }

        private FieldNode resolveField(FieldInsnNode access) {
            return resolve(access.owner, classNode -> classNode.fields.stream()
                .filter(field -> field.name.equals(access.name) && field.desc.equals(access.desc))
                .findFirst().orElse(null));
        }

        private MethodNode resolveMethod(MethodInsnNode call) {
            return resolve(call.owner, classNode -> classNode.methods.stream()
                .filter(method -> method.name.equals(call.name) && method.desc.equals(call.desc))
                .findFirst().orElse(null));
        }

        // An instruction names the class it was compiled against, which may inherit the member.
        private <T> T resolve(String owner, Function<ClassNode, T> declared) {
            Set<String> visited = new HashSet<>();
            List<String> pending = new ArrayList<>(List.of(owner));
            while (!pending.isEmpty()) {
                ClassNode classNode = byName.get(pending.removeFirst());
                if (classNode == null || !visited.add(classNode.name)) {
                    continue;
                }
                T member = declared.apply(classNode);
                if (member != null) {
                    return member;
                }
                if (classNode.superName != null) {
                    pending.add(classNode.superName);
                }
                pending.addAll(classNode.interfaces);
            }
            return null;
        }

        private static void add(Map<String, Integer> features, String feature, int weight) {
            features.merge(feature, weight, Integer::sum);
        }
    }

    // Members of the source and target JARs that could match each other.
    private static final class Group<M> {
        private final List<M> sources = new ArrayList<>();
        private final List<M> targets = new ArrayList<>();

        List<M> side(boolean source) {
            return source ? sources : targets;
        }
    }

    // Identity matters: two members may be equal in every field but still be different declarations.
    private static final class Member<N> {
        private final ClassNode owner;
        private final N node;

        Member(ClassNode owner, N node) {
            this.owner = owner;
            this.node = node;
        }

        ClassNode owner() {
            return owner;
        }

        N node() {
            return node;
        }
    }

    private record Score(int percent, Reason reason) {
    }

    // Matched members numbered alike on both sides, so features that name them can be compared.
    private record Ids<N>(Map<N, Integer> source, Map<N, Integer> target) {
        static <N> Ids<N> of(List<Pair<N>> pairs) {
            Map<N, Integer> source = new IdentityHashMap<>();
            Map<N, Integer> target = new IdentityHashMap<>();
            for (int index = 0; index < pairs.size(); index++) {
                source.put(pairs.get(index).source().node(), index);
                target.put(pairs.get(index).target().node(), index);
            }
            return new Ids<>(source, target);
        }
    }

    private record Candidate<N>(Member<N> source, Member<N> target, int percent, Reason reason) {
    }

    private record Pair<N>(Member<N> source, Member<N> target, int percent, Reason reason) {
        MemberMatch toMatch(Function<N, String> name, Function<N, String> descriptor) {
            return new MemberMatch(
                new MemberRef(source.owner().name, name.apply(source.node()), descriptor.apply(source.node())),
                new MemberRef(target.owner().name, name.apply(target.node()), descriptor.apply(target.node())),
                percent,
                reason
            );
        }
    }

    @FunctionalInterface
    private interface Scorer<N> {
        Score score(Member<N> source, Member<N> target);
    }
}
