package org.de.rikr.project;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.behavioral.MethodComparator;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.ClassMappingService;
import org.de.rikr.mapping.ClassMatch;
import org.de.rikr.mapping.SimilarClassReport;
import org.de.rikr.model.ClassRef;
import org.de.rikr.model.Workspace;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Comparing classes across JARs and recording which classes map to each other.
 */
public class MappingOperations {
    private static final int MIN_CLASS_MATCH_PERCENT = 50;
    private static final int MAX_CLASS_MATCHES_PER_JAR = 10;

    private final Workspace workspace;
    private final ClassComparator classComparator = new ClassComparator();
    private final MethodComparator methodComparator = new MethodComparator();
    private final ClassMappingService service = new ClassMappingService(classComparator);

    MappingOperations(Workspace workspace) {
        this.workspace = workspace;
    }

    public List<ClassMatch> findCandidates(
        String targetJar,
        ClassNode sourceClass,
        int minimumSimilarityPercent,
        int maximumResults
    ) {
        List<ClassNode> targetClasses = workspace.classesSnapshot(targetJar);
        if (targetClasses == null) {
            return List.of();
        }
        return service.findCandidates(targetClasses, sourceClass, minimumSimilarityPercent, maximumResults);
    }

    /**
     * The closest matches of the class in every other JAR, best first.
     */
    public Map<String, List<ClassMatch>> findSimilarClasses(String jarToExclude, ClassNode classNode) {
        Map<String, List<ClassMatch>> matching = new LinkedHashMap<>();
        workspace.classesByJar().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .filter(entry -> !entry.getKey().equals(jarToExclude))
            .forEach(entry -> {
                List<ClassMatch> matches = classComparator.getClassNodeMatches(entry.getValue(), classNode).stream()
                    .filter(match -> match.similarityPercent() >= MIN_CLASS_MATCH_PERCENT)
                    .limit(MAX_CLASS_MATCHES_PER_JAR)
                    .toList();
                if (!matches.isEmpty()) {
                    matching.put(entry.getKey(), matches);
                }
            });
        return matching;
    }

    public void accept(String sourceJar, String targetJar, ClassNode sourceClass, ClassMatch candidate) {
        service.accept(sourceJar, targetJar, sourceClass, candidate);
    }

    public void reject(String sourceJar, String targetJar, ClassNode sourceClass, ClassNode targetClass) {
        service.reject(sourceJar, targetJar, sourceClass, targetClass);
    }

    public void clearDecisions(String sourceJar, String targetJar, ClassNode sourceClass) {
        service.clearSourceDecisions(sourceJar, targetJar, sourceClass);
    }

    public Optional<ClassMappingDecision> accepted(String sourceJar, String targetJar, ClassNode sourceClass) {
        return service.getAcceptedMapping(sourceJar, targetJar, sourceClass);
    }

    public boolean isRejected(String sourceJar, String targetJar, ClassNode sourceClass, ClassNode targetClass) {
        return service.isRejected(sourceJar, targetJar, sourceClass, targetClass);
    }

    public List<ClassMappingDecision> accepted(String sourceJar, String targetJar) {
        return service.getAcceptedMappings(sourceJar, targetJar);
    }

    public List<ClassMappingDecision> allAccepted() {
        return service.getAllAcceptedMappings();
    }

    public List<ClassMappingDecision> autoAcceptUnique(String sourceJar, String targetJar, int minimumSimilarityPercent) {
        List<ClassNode> sourceClasses = workspace.classesSnapshot(sourceJar);
        List<ClassNode> targetClasses = workspace.classesSnapshot(targetJar);
        if (sourceClasses == null || targetClasses == null) {
            return List.of();
        }
        return service.autoAcceptUniqueMatches(
            sourceJar,
            targetJar,
            sourceClasses,
            targetClasses,
            minimumSimilarityPercent
        );
    }

    public void export(File destination, String sourceJar, String targetJar) throws IOException {
        service.exportAcceptedMappings(destination, sourceJar, targetJar);
    }

    public List<String> nonStaticFieldDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticFieldDescriptions(classNode);
    }

    public List<String> nonStaticMethodDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticMethodDescriptions(classNode);
    }

    /**
     * The closest matches of the class in every other JAR, and for each match the methods with the same
     * normalized descriptor that also behave the same when simulated.
     */
    public SimilarClassReport similarClassReport(String sourceJar, ClassNode sourceClass) {
        List<SimilarClassReport.Match> matches = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<String, List<ClassMatch>> entry : findSimilarClasses(sourceJar, sourceClass).entrySet()) {
            for (ClassMatch match : entry.getValue()) {
                List<SimilarClassReport.MethodPair> equivalent = new ArrayList<>();
                for (MethodNode sourceMethod : sourceClass.methods) {
                    String descriptor = classComparator.normalizeDescriptor(sourceMethod.desc);
                    for (MethodNode targetMethod : match.classNode().methods) {
                        if (!descriptor.equals(classComparator.normalizeDescriptor(targetMethod.desc))) {
                            continue;
                        }
                        try {
                            if (methodComparator.areBehaviorallyEquivalent(sourceClass.name, sourceMethod,
                                match.classNode().name, targetMethod)) {
                                equivalent.add(new SimilarClassReport.MethodPair(sourceMethod, targetMethod));
                            }
                        } catch (RuntimeException exception) {
                            // The simulator does not support every instruction; count it rather than fail the report.
                            skipped++;
                        }
                    }
                }
                matches.add(new SimilarClassReport.Match(entry.getKey(), match, equivalent));
            }
        }
        return new SimilarClassReport(matches, skipped);
    }

    /**
     * Replace every decision with saved ones, which must name loaded classes.
     */
    void restore(List<ClassMappingDecision> mappings) {
        service.clear();
        for (ClassMappingDecision mapping : mappings) {
            requireClass(mapping.sourceJar(), mapping.sourceClassName());
            requireClass(mapping.targetJar(), mapping.targetClassName());
            service.restore(mapping);
        }
    }

    void renameClass(String jar, String oldName, String newName) {
        service.renameClass(jar, oldName, newName);
    }

    void renameClasses(String jar, Map<String, String> renames) {
        service.renameClasses(jar, renames);
    }

    void removeClass(String jar, String className) {
        service.clearClass(jar, className);
    }

    void removeJar(String jar) {
        service.clearJar(jar);
    }

    private void requireClass(String jar, String className) {
        if (!workspace.contains(jar)) {
            throw new IllegalArgumentException("Project references an unloaded JAR: " + jar);
        }
        if (workspace.findClass(new ClassRef(jar, className)).isEmpty()) {
            throw new IllegalArgumentException("Project references a missing class: " + jar + " -> " + className);
        }
    }
}
