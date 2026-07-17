package org.de.rikr.service;

import org.de.rikr.behavioral.ClassComparator;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ClassMappingService {
    private final ClassComparator classComparator;
    private final Map<SourceKey, ClassMappingDecision> acceptedMappings = new LinkedHashMap<>();
    private final Set<CandidateKey> rejectedMappings = new LinkedHashSet<>();

    public ClassMappingService(ClassComparator classComparator) {
        this.classComparator = Objects.requireNonNull(classComparator, "classComparator");
    }

    public List<ClassMatch> findCandidates(
        List<ClassNode> targetClasses,
        ClassNode sourceClass,
        int minimumSimilarityPercent,
        int maximumResults
    ) {
        Objects.requireNonNull(targetClasses, "targetClasses");
        Objects.requireNonNull(sourceClass, "sourceClass");
        validatePercent(minimumSimilarityPercent);
        if (maximumResults < 1) {
            throw new IllegalArgumentException("maximumResults must be positive");
        }

        return classComparator.getClassNodeMatches(targetClasses, sourceClass).stream()
            .filter(match -> match.similarityPercent() >= minimumSimilarityPercent)
            .limit(maximumResults)
            .toList();
    }

    public synchronized void accept(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassMatch candidate
    ) {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(candidate, "candidate");
        SourceKey sourceKey = sourceKey(sourceJar, targetJar, sourceClass.name);
        CandidateKey candidateKey = candidateKey(sourceKey, candidate.classNode().name);

        Optional<ClassMappingDecision> conflictingMapping = acceptedMappings.values().stream()
            .filter(mapping -> mapping.sourceJar().equals(sourceJar))
            .filter(mapping -> mapping.targetJar().equals(targetJar))
            .filter(mapping -> mapping.targetClassName().equals(candidate.classNode().name))
            .filter(mapping -> !mapping.sourceClassName().equals(sourceClass.name))
            .findFirst();
        if (conflictingMapping.isPresent()) {
            throw new IllegalArgumentException(
                candidate.classNode().name + " is already mapped from " + conflictingMapping.get().sourceClassName()
            );
        }

        acceptedMappings.put(sourceKey, new ClassMappingDecision(
            sourceJar,
            targetJar,
            sourceClass.name,
            candidate.classNode().name,
            candidate.similarityPercent()
        ));
        rejectedMappings.remove(candidateKey);
    }

    public synchronized void reject(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(targetClass, "targetClass");
        SourceKey sourceKey = sourceKey(sourceJar, targetJar, sourceClass.name);
        CandidateKey candidateKey = candidateKey(sourceKey, targetClass.name);
        ClassMappingDecision accepted = acceptedMappings.get(sourceKey);
        if (accepted != null && accepted.targetClassName().equals(targetClass.name)) {
            acceptedMappings.remove(sourceKey);
        }
        rejectedMappings.add(candidateKey);
    }

    public synchronized void clearSourceDecisions(String sourceJar, String targetJar, ClassNode sourceClass) {
        SourceKey sourceKey = sourceKey(sourceJar, targetJar, sourceClass.name);
        acceptedMappings.remove(sourceKey);
        rejectedMappings.removeIf(key -> key.sourceKey().equals(sourceKey));
    }

    public synchronized Optional<ClassMappingDecision> getAcceptedMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass
    ) {
        return Optional.ofNullable(acceptedMappings.get(sourceKey(sourceJar, targetJar, sourceClass.name)));
    }

    public synchronized boolean isRejected(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        SourceKey sourceKey = sourceKey(sourceJar, targetJar, sourceClass.name);
        return rejectedMappings.contains(candidateKey(sourceKey, targetClass.name));
    }

    public synchronized List<ClassMappingDecision> getAcceptedMappings(String sourceJar, String targetJar) {
        return acceptedMappings.values().stream()
            .filter(mapping -> mapping.sourceJar().equals(sourceJar))
            .filter(mapping -> mapping.targetJar().equals(targetJar))
            .sorted(Comparator.comparing(ClassMappingDecision::sourceClassName))
            .toList();
    }

    public synchronized void clearJar(String jarName) {
        Objects.requireNonNull(jarName, "jarName");
        acceptedMappings.entrySet().removeIf(entry ->
            entry.getKey().sourceJar().equals(jarName) || entry.getKey().targetJar().equals(jarName)
        );
        rejectedMappings.removeIf(entry ->
            entry.sourceKey().sourceJar().equals(jarName) || entry.sourceKey().targetJar().equals(jarName)
        );
    }

    public synchronized void clearClass(String jarName, String className) {
        Objects.requireNonNull(jarName, "jarName");
        Objects.requireNonNull(className, "className");
        acceptedMappings.entrySet().removeIf(entry -> {
            ClassMappingDecision mapping = entry.getValue();
            return mapping.sourceJar().equals(jarName) && mapping.sourceClassName().equals(className)
                || mapping.targetJar().equals(jarName) && mapping.targetClassName().equals(className);
        });
        rejectedMappings.removeIf(entry -> {
            SourceKey sourceKey = entry.sourceKey();
            return sourceKey.sourceJar().equals(jarName) && sourceKey.sourceClassName().equals(className)
                || sourceKey.targetJar().equals(jarName) && entry.targetClassName().equals(className);
        });
    }

    public List<ClassMappingDecision> autoAcceptUniqueMatches(
        String sourceJar,
        String targetJar,
        List<ClassNode> sourceClasses,
        List<ClassNode> targetClasses,
        int minimumSimilarityPercent
    ) {
        Objects.requireNonNull(sourceClasses, "sourceClasses");
        Objects.requireNonNull(targetClasses, "targetClasses");
        validatePercent(minimumSimilarityPercent);

        Set<String> usedTargets = new HashSet<>();
        for (ClassMappingDecision mapping : getAcceptedMappings(sourceJar, targetJar)) {
            usedTargets.add(mapping.targetClassName());
        }

        List<AutoMatch> proposals = new ArrayList<>();
        sourceClasses.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(classNode -> classNode.name))
            .filter(sourceClass -> getAcceptedMapping(sourceJar, targetJar, sourceClass).isEmpty())
            .forEach(sourceClass -> uniqueBestMatch(
                sourceJar,
                targetJar,
                sourceClass,
                targetClasses,
                minimumSimilarityPercent
            ).ifPresent(match -> proposals.add(new AutoMatch(sourceClass, match))));

        proposals.sort(
            Comparator.comparingInt((AutoMatch match) -> match.candidate().similarityPercent()).reversed()
                .thenComparing(match -> match.sourceClass().name)
                .thenComparing(match -> match.candidate().classNode().name)
        );

        List<ClassMappingDecision> added = new ArrayList<>();
        for (AutoMatch proposal : proposals) {
            if (getAcceptedMapping(sourceJar, targetJar, proposal.sourceClass()).isPresent()) {
                continue;
            }
            if (!usedTargets.add(proposal.candidate().classNode().name)) {
                continue;
            }
            try {
                accept(sourceJar, targetJar, proposal.sourceClass(), proposal.candidate());
                getAcceptedMapping(sourceJar, targetJar, proposal.sourceClass()).ifPresent(added::add);
            } catch (IllegalArgumentException ignored) {
                // Another mapping may have claimed this target while proposals were calculated.
            }
        }

        return List.copyOf(added);
    }

    public synchronized void exportAcceptedMappings(File destination, String sourceJar, String targetJar)
        throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sourceJar, "sourceJar");
        Objects.requireNonNull(targetJar, "targetJar");
        List<ClassMappingDecision> mappings = getAcceptedMappings(sourceJar, targetJar);
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"sourceJar\": \"").append(escapeJson(sourceJar)).append("\",\n");
        json.append("  \"targetJar\": \"").append(escapeJson(targetJar)).append("\",\n");
        json.append("  \"mappings\": [");
        if (!mappings.isEmpty()) {
            json.append('\n');
        }

        for (int index = 0; index < mappings.size(); index++) {
            ClassMappingDecision mapping = mappings.get(index);
            json.append("    {\"source\": \"")
                .append(escapeJson(mapping.sourceClassName()))
                .append("\", \"target\": \"")
                .append(escapeJson(mapping.targetClassName()))
                .append("\", \"similarityPercent\": ")
                .append(mapping.similarityPercent())
                .append('}');
            if (index < mappings.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }

        json.append("  ]\n");
        json.append("}\n");
        Files.writeString(destination.toPath(), json.toString(), StandardCharsets.UTF_8);
    }

    private Optional<ClassMatch> uniqueBestMatch(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        List<ClassNode> targetClasses,
        int minimumSimilarityPercent
    ) {
        List<ClassMatch> candidates = classComparator.getClassNodeMatches(targetClasses, sourceClass).stream()
            .filter(match -> match.similarityPercent() >= minimumSimilarityPercent)
            .filter(match -> !isRejected(sourceJar, targetJar, sourceClass, match.classNode()))
            .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        ClassMatch best = candidates.get(0);
        if (candidates.size() > 1 && candidates.get(1).similarityPercent() == best.similarityPercent()) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private SourceKey sourceKey(String sourceJar, String targetJar, String sourceClassName) {
        Objects.requireNonNull(sourceJar, "sourceJar");
        Objects.requireNonNull(targetJar, "targetJar");
        Objects.requireNonNull(sourceClassName, "sourceClassName");
        if (sourceJar.equals(targetJar)) {
            throw new IllegalArgumentException("Source and target JARs must be different");
        }
        return new SourceKey(sourceJar, targetJar, sourceClassName);
    }

    private CandidateKey candidateKey(SourceKey sourceKey, String targetClassName) {
        return new CandidateKey(sourceKey, Objects.requireNonNull(targetClassName, "targetClassName"));
    }

    private void validatePercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("minimumSimilarityPercent must be between 0 and 100");
        }
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private record SourceKey(String sourceJar, String targetJar, String sourceClassName) {
    }

    private record CandidateKey(SourceKey sourceKey, String targetClassName) {
    }

    private record AutoMatch(ClassNode sourceClass, ClassMatch candidate) {
    }
}
