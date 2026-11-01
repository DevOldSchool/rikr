package org.de.rikr.project;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.behavioral.MethodComparator;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.ClassMappingService;
import org.de.rikr.mapping.ClassMatch;
import org.de.rikr.mapping.ClassVoting;
import org.de.rikr.mapping.MemberMatcher;
import org.de.rikr.mapping.MemberMatches;
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
import java.util.concurrent.Callable;

/**
 * Comparing classes across JARs and recording which classes map to each other.
 */
public class MappingOperations {
    private static final int MIN_CLASS_MATCH_PERCENT = 50;
    private static final int MAX_CLASS_MATCHES_PER_JAR = 10;

    private final Workspace workspace;
    private final EventBus events;
    private final ClassComparator classComparator = new ClassComparator();
    private final MethodComparator methodComparator = new MethodComparator();
    private final ClassMappingService service = new ClassMappingService(classComparator);

    MappingOperations(Workspace workspace, EventBus events) {
        this.workspace = workspace;
        this.events = events;
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
        events.publish(new ProjectEvent.MappingsChanged());
    }

    public void reject(String sourceJar, String targetJar, ClassNode sourceClass, ClassNode targetClass) {
        service.reject(sourceJar, targetJar, sourceClass, targetClass);
        events.publish(new ProjectEvent.MappingsChanged());
    }

    public void clearDecisions(String sourceJar, String targetJar, ClassNode sourceClass) {
        service.clearSourceDecisions(sourceJar, targetJar, sourceClass);
        events.publish(new ProjectEvent.MappingsChanged());
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
        List<ClassMappingDecision> accepted = service.autoAcceptUniqueMatches(
            sourceJar,
            targetJar,
            sourceClasses,
            targetClasses,
            minimumSimilarityPercent
        );
        if (!accepted.isEmpty()) {
            events.publish(new ProjectEvent.MappingsChanged());
        }
        return accepted;
    }

    /**
     * Match the fields and methods of the source JAR to the target JAR's, using the accepted class mappings
     * between them. Reads the classes without changing them.
     */
    public MemberMatches matchMembers(String sourceJar, String targetJar) {
        try {
            return matchMembersLater(sourceJar, targetJar, false).call();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * {@link #matchMembers} as work that can run on another thread: it uses copies of the classes taken now, so
     * renames made meanwhile don't change what it reads. The matches name members as they are now.
     */
    public Callable<MemberMatches> matchMembersLater(String sourceJar, String targetJar) {
        return matchMembersLater(sourceJar, targetJar, true);
    }

    private Callable<MemberMatches> matchMembersLater(String sourceJar, String targetJar, boolean copy) {
        List<ClassNode> sourceClasses = workspace.classesSnapshot(sourceJar);
        List<ClassNode> targetClasses = workspace.classesSnapshot(targetJar);
        if (sourceClasses == null || targetClasses == null) {
            return () -> new MemberMatches(List.of(), List.of(), 0, 0);
        }
        List<ClassNode> sources = copy ? copies(sourceClasses) : sourceClasses;
        List<ClassNode> targets = copy ? copies(targetClasses) : targetClasses;
        Map<String, String> classMapping = new LinkedHashMap<>();
        for (ClassMappingDecision decision : accepted(sourceJar, targetJar)) {
            classMapping.put(decision.sourceClassName(), decision.targetClassName());
        }
        return () -> new MemberMatcher().match(sources, targets, classMapping);
    }

    /**
     * Work that finds more class matches from the members of the classes matched so far (see {@link ClassVoting}),
     * on copies of the classes taken now, so it can run on another thread. Apply the result with
     * {@link #acceptVoted}.
     */
    public Callable<List<ClassVoting.VotedMatch>> voteClassesLater(String sourceJar, String targetJar) {
        List<ClassNode> sourceClasses = workspace.classesSnapshot(sourceJar);
        List<ClassNode> targetClasses = workspace.classesSnapshot(targetJar);
        if (sourceClasses == null || targetClasses == null) {
            return List::of;
        }
        List<ClassNode> sources = copies(sourceClasses);
        List<ClassNode> targets = copies(targetClasses);
        Map<String, String> classMapping = new LinkedHashMap<>();
        for (ClassMappingDecision decision : accepted(sourceJar, targetJar)) {
            classMapping.put(decision.sourceClassName(), decision.targetClassName());
        }
        return () -> new ClassVoting().match(sources, targets, classMapping);
    }

    /**
     * Accept class matches found by {@link #voteClassesLater}. A match is skipped when either class was renamed,
     * removed or matched otherwise in the meantime.
     *
     * @return the mappings accepted
     */
    public List<ClassMappingDecision> acceptVoted(String sourceJar, String targetJar,
        List<ClassVoting.VotedMatch> matches) {
        List<ClassMappingDecision> added = new ArrayList<>();
        for (ClassVoting.VotedMatch match : matches) {
            Optional<ClassNode> source = workspace.findClass(new ClassRef(sourceJar, match.source()));
            Optional<ClassNode> target = workspace.findClass(new ClassRef(targetJar, match.target()));
            if (source.isEmpty() || target.isEmpty() || accepted(sourceJar, targetJar, source.get()).isPresent()) {
                continue;
            }
            try {
                service.accept(sourceJar, targetJar, source.get(), new ClassMatch(target.get(), match.confidencePercent()));
                accepted(sourceJar, targetJar, source.get()).ifPresent(added::add);
            } catch (IllegalArgumentException alreadyMapped) {
                // Another source class claimed this target meanwhile; the earlier decision stands.
            }
        }
        if (!added.isEmpty()) {
            events.publish(new ProjectEvent.MappingsChanged());
        }
        return added;
    }

    private static List<ClassNode> copies(List<ClassNode> classes) {
        List<ClassNode> copies = new ArrayList<>(classes.size());
        for (ClassNode classNode : classes) {
            ClassNode copy = new ClassNode();
            classNode.accept(copy);
            copies.add(copy);
        }
        return copies;
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
