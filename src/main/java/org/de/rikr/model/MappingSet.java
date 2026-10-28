package org.de.rikr.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Accepted and rejected class mappings between JARs. Each source class maps to at most one class of a target
 * JAR, and each target class is claimed by at most one source class.
 * <p>
 * Mappings follow class renames instead of being dropped, so renaming members or classes keeps the user's
 * decisions.
 */
public class MappingSet {
    private final Map<Side, ClassMappingDecision> accepted = new LinkedHashMap<>();
    private final Set<Rejection> rejected = new LinkedHashSet<>();

    /**
     * Accept a mapping, replacing any earlier one for its source class.
     *
     * @throws IllegalArgumentException if the target class is already mapped from another source class
     */
    public synchronized void accept(ClassMappingDecision decision) {
        Objects.requireNonNull(decision, "decision");
        Side side = side(source(decision), decision.targetJar());
        ClassRef target = target(decision);
        accepted.values().stream()
            .filter(mapping -> mapping.sourceJar().equals(decision.sourceJar()))
            .filter(mapping -> target(mapping).equals(target))
            .filter(mapping -> !mapping.sourceClassName().equals(decision.sourceClassName()))
            .findFirst()
            .ifPresent(conflict -> {
                throw new IllegalArgumentException(
                    target.className() + " is already mapped from " + conflict.sourceClassName()
                );
            });
        accepted.put(side, decision);
        rejected.remove(new Rejection(side.source(), target));
    }

    /**
     * Reject a candidate, removing it first if it was the accepted mapping.
     */
    public synchronized void reject(ClassRef source, ClassRef target) {
        Side side = side(source, target.jar());
        ClassMappingDecision current = accepted.get(side);
        if (current != null && target(current).equals(target)) {
            accepted.remove(side);
        }
        rejected.add(new Rejection(source, target));
    }

    /**
     * Forget every decision for the source class towards the target JAR.
     */
    public synchronized void clearSource(ClassRef source, String targetJar) {
        Side side = side(source, targetJar);
        accepted.remove(side);
        rejected.removeIf(rejection -> rejection.source().equals(source) && rejection.target().jar().equals(targetJar));
    }

    public synchronized Optional<ClassMappingDecision> accepted(ClassRef source, String targetJar) {
        return Optional.ofNullable(accepted.get(side(source, targetJar)));
    }

    public synchronized boolean isRejected(ClassRef source, ClassRef target) {
        return rejected.contains(new Rejection(source, target));
    }

    public synchronized List<ClassMappingDecision> accepted(String sourceJar, String targetJar) {
        return accepted.values().stream()
            .filter(mapping -> mapping.sourceJar().equals(sourceJar))
            .filter(mapping -> mapping.targetJar().equals(targetJar))
            .sorted(Comparator.comparing(ClassMappingDecision::sourceClassName))
            .toList();
    }

    public synchronized List<ClassMappingDecision> allAccepted() {
        return accepted.values().stream()
            .sorted(Comparator.comparing(ClassMappingDecision::sourceJar)
                .thenComparing(ClassMappingDecision::targetJar)
                .thenComparing(ClassMappingDecision::sourceClassName))
            .toList();
    }

    public synchronized void clear() {
        accepted.clear();
        rejected.clear();
    }

    public synchronized void removeJar(String jar) {
        Objects.requireNonNull(jar, "jar");
        accepted.values().removeIf(mapping -> mapping.sourceJar().equals(jar) || mapping.targetJar().equals(jar));
        rejected.removeIf(rejection -> rejection.source().jar().equals(jar) || rejection.target().jar().equals(jar));
    }

    public synchronized void removeClass(ClassRef ref) {
        Objects.requireNonNull(ref, "ref");
        accepted.values().removeIf(mapping -> source(mapping).equals(ref) || target(mapping).equals(ref));
        rejected.removeIf(rejection -> rejection.source().equals(ref) || rejection.target().equals(ref));
    }

    /**
     * Follow a class rename: every decision that names the class now names it by its new name.
     */
    public synchronized void renameClass(ClassRef ref, String newName) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(newName, "newName");
        ClassRef renamed = ref.withClassName(newName);
        if (renamed.equals(ref)) {
            return;
        }

        Map<Side, ClassMappingDecision> updatedAccepted = new LinkedHashMap<>();
        for (ClassMappingDecision mapping : accepted.values()) {
            ClassMappingDecision updated = new ClassMappingDecision(
                mapping.sourceJar(),
                mapping.targetJar(),
                rename(source(mapping), ref, renamed).className(),
                rename(target(mapping), ref, renamed).className(),
                mapping.similarityPercent()
            );
            updatedAccepted.put(side(source(updated), updated.targetJar()), updated);
        }
        accepted.clear();
        accepted.putAll(updatedAccepted);

        Set<Rejection> updatedRejected = new LinkedHashSet<>();
        for (Rejection rejection : rejected) {
            updatedRejected.add(new Rejection(
                rename(rejection.source(), ref, renamed),
                rename(rejection.target(), ref, renamed)
            ));
        }
        rejected.clear();
        rejected.addAll(updatedRejected);
    }

    private static ClassRef rename(ClassRef value, ClassRef from, ClassRef to) {
        return value.equals(from) ? to : value;
    }

    private static ClassRef source(ClassMappingDecision mapping) {
        return new ClassRef(mapping.sourceJar(), mapping.sourceClassName());
    }

    private static ClassRef target(ClassMappingDecision mapping) {
        return new ClassRef(mapping.targetJar(), mapping.targetClassName());
    }

    private static Side side(ClassRef source, String targetJar) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetJar, "targetJar");
        if (source.jar().equals(targetJar)) {
            throw new IllegalArgumentException("Source and target JARs must be different");
        }
        return new Side(source, targetJar);
    }

    private record Side(ClassRef source, String targetJar) {
    }

    private record Rejection(ClassRef source, ClassRef target) {
    }
}
