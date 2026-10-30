package org.de.rikr.mapping;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.model.ClassRef;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ClassMappingService {
    private final ClassComparator classComparator;
    private final MappingSet mappings = new MappingSet();

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

    public void accept(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassMatch candidate
    ) {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(candidate, "candidate");
        mappings.accept(new ClassMappingDecision(
            sourceJar,
            targetJar,
            sourceClass.name,
            candidate.classNode().name,
            candidate.similarityPercent()
        ));
    }

    public void reject(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(targetClass, "targetClass");
        mappings.reject(new ClassRef(sourceJar, sourceClass.name), new ClassRef(targetJar, targetClass.name));
    }

    public void clearSourceDecisions(String sourceJar, String targetJar, ClassNode sourceClass) {
        mappings.clearSource(new ClassRef(sourceJar, sourceClass.name), targetJar);
    }

    public Optional<ClassMappingDecision> getAcceptedMapping(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass
    ) {
        return mappings.accepted(new ClassRef(sourceJar, sourceClass.name), targetJar);
    }

    public boolean isRejected(
        String sourceJar,
        String targetJar,
        ClassNode sourceClass,
        ClassNode targetClass
    ) {
        return mappings.isRejected(
            new ClassRef(sourceJar, sourceClass.name),
            new ClassRef(targetJar, targetClass.name)
        );
    }

    public List<ClassMappingDecision> getAcceptedMappings(String sourceJar, String targetJar) {
        return mappings.accepted(sourceJar, targetJar);
    }

    public List<ClassMappingDecision> getAllAcceptedMappings() {
        return mappings.allAccepted();
    }

    public void restore(ClassMappingDecision decision) {
        mappings.accept(decision);
    }

    public void clear() {
        mappings.clear();
    }

    public void clearJar(String jarName) {
        mappings.removeJar(jarName);
    }

    public void clearClass(String jarName, String className) {
        mappings.removeClass(new ClassRef(jarName, className));
    }

    /**
     * Keep the decisions of a class that was renamed.
     */
    public void renameClass(String jarName, String oldName, String newName) {
        mappings.renameClass(new ClassRef(jarName, oldName), newName);
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

    public void exportAcceptedMappings(File destination, String sourceJar, String targetJar)
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

    private record AutoMatch(ClassNode sourceClass, ClassMatch candidate) {
    }
}
