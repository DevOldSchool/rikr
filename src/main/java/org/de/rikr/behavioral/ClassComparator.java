package org.de.rikr.behavioral;

import org.de.rikr.mapping.ClassMatch;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class ClassComparator {
    // Any class type outside java/; String.replaceAll would compile this again for every descriptor.
    private static final Pattern APPLICATION_TYPE = Pattern.compile("L(?!java/).*?;");

    public List<ClassNode> getMatchingClassNodes(List<ClassNode> classes, ClassNode classNodeToMatch) {
        return getClassNodeMatches(classes, classNodeToMatch).stream()
            .filter(match -> match.similarityPercent() == 100)
            .map(ClassMatch::classNode)
            .toList();
    }

    public List<ClassMatch> getClassNodeMatches(List<ClassNode> classes, ClassNode classNodeToMatch) {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(classNodeToMatch, "classNodeToMatch");

        return getClassNodeMatches(signatures(classes), signature(classNodeToMatch));
    }

    /**
     * {@link #getClassNodeMatches(List, ClassNode)} for classes whose signatures were worked out already, which is
     * what makes comparing every class of one JAR with every class of another affordable.
     */
    public List<ClassMatch> getClassNodeMatches(List<Signature> candidates, Signature source) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(source, "source");

        return candidates.stream()
            .map(candidate -> new ClassMatch(candidate.classNode(), similarityPercent(source, candidate)))
            .filter(match -> match.similarityPercent() > 0)
            .sorted((left, right) -> {
                int scoreComparison = Integer.compare(right.similarityPercent(), left.similarityPercent());
                if (scoreComparison != 0) {
                    return scoreComparison;
                }

                return left.classNode().name.compareTo(right.classNode().name);
            })
            .toList();
    }

    public int getSimilarityPercent(ClassNode source, ClassNode candidate) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(candidate, "candidate");

        return similarityPercent(signature(source), signature(candidate));
    }

    /**
     * The parts of a class that are compared, as the class is now; a signature is not updated when the class
     * changes, so it is meant to be used straight away.
     */
    public Signature signature(ClassNode classNode) {
        Objects.requireNonNull(classNode, "classNode");
        Map<String, Integer> partCounts = new HashMap<>();
        List<String> parts = getSignatureParts(classNode);
        for (String part : parts) {
            partCounts.merge(part, 1, Integer::sum);
        }
        return new Signature(classNode, Modifier.isInterface(classNode.access), Map.copyOf(partCounts), parts.size());
    }

    public List<Signature> signatures(List<ClassNode> classes) {
        return classes.stream()
            .filter(Objects::nonNull)
            .map(this::signature)
            .toList();
    }

    /**
     * @param partCounts how many times each part occurs
     * @param size       how many parts there are
     */
    public record Signature(ClassNode classNode, boolean isInterface, Map<String, Integer> partCounts, int size) {
    }

    private static int similarityPercent(Signature source, Signature candidate) {
        if (source.isInterface() != candidate.isInterface()) {
            return 0;
        }

        // Each part of the source matches at most one part of the candidate.
        int matchingPartCount = 0;
        for (Map.Entry<String, Integer> part : source.partCounts().entrySet()) {
            matchingPartCount += Math.min(part.getValue(), candidate.partCounts().getOrDefault(part.getKey(), 0));
        }

        return (int) Math.round(200.0 * matchingPartCount / (source.size() + candidate.size()));
    }

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return classNode.fields.stream()
            .filter(field -> !Modifier.isStatic(field.access))
            .map(field -> normalizeDescriptor(field.desc))
            .sorted()
            .toList();
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return classNode.methods.stream()
            .filter(method -> !Modifier.isStatic(method.access))
            .map(method -> normalizeDescriptor(method.desc))
            .sorted()
            .toList();
    }

    public String normalizeDescriptor(String descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return APPLICATION_TYPE.matcher(descriptor).replaceAll("L?;");
    }

    private List<String> getSignatureParts(ClassNode classNode) {
        List<String> parts = new java.util.ArrayList<>();
        parts.add("abstract:" + Modifier.isAbstract(classNode.access));
        parts.add("superclass:" + hasSuperclass(classNode));

        for (int index = 0; index < classNode.interfaces.size(); index++) {
            parts.add("interface");
        }
        getNonStaticFieldDescriptions(classNode).forEach(description -> parts.add("field:" + description));
        getNonStaticMethodDescriptions(classNode).forEach(description -> parts.add("method:" + description));

        return parts;
    }

    private boolean hasSuperclass(ClassNode classNode) {
        return classNode.superName != null && !"java/lang/Object".equals(classNode.superName);
    }
}
