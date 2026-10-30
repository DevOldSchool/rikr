package org.de.rikr.behavioral;

import org.de.rikr.mapping.ClassMatch;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClassComparator {

    public List<ClassNode> getMatchingClassNodes(List<ClassNode> classes, ClassNode classNodeToMatch) {
        return getClassNodeMatches(classes, classNodeToMatch).stream()
            .filter(match -> match.similarityPercent() == 100)
            .map(ClassMatch::classNode)
            .toList();
    }

    public List<ClassMatch> getClassNodeMatches(List<ClassNode> classes, ClassNode classNodeToMatch) {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(classNodeToMatch, "classNodeToMatch");

        return classes.stream()
            .filter(Objects::nonNull)
            .map(classNode -> new ClassMatch(classNode, getSimilarityPercent(classNodeToMatch, classNode)))
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

        if (Modifier.isInterface(source.access) != Modifier.isInterface(candidate.access)) {
            return 0;
        }

        List<String> sourceSignature = getSignatureParts(source);
        List<String> candidateSignature = getSignatureParts(candidate);
        int matchingPartCount = getMatchingPartCount(sourceSignature, candidateSignature);

        return (int) Math.round(200.0 * matchingPartCount
            / (sourceSignature.size() + candidateSignature.size()));
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
        return descriptor.replaceAll("L(?!java/).*?;", "L?;");
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

    private int getMatchingPartCount(List<String> sourceParts, List<String> candidateParts) {
        Map<String, Integer> candidatePartCounts = new HashMap<>();
        for (String part : candidateParts) {
            candidatePartCounts.merge(part, 1, Integer::sum);
        }

        int matchingPartCount = 0;
        for (String part : sourceParts) {
            int availableCount = candidatePartCounts.getOrDefault(part, 0);
            if (availableCount > 0) {
                matchingPartCount++;
                candidatePartCounts.put(part, availableCount - 1);
            }
        }

        return matchingPartCount;
    }

    private boolean hasSuperclass(ClassNode classNode) {
        return classNode.superName != null && !"java/lang/Object".equals(classNode.superName);
    }
}
