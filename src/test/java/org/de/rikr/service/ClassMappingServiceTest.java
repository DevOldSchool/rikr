package org.de.rikr.service;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.model.ClassMappingDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassMappingServiceTest {
    private final ClassMappingService service = new ClassMappingService(new ClassComparator());

    @Test
    void ranksAndFiltersCandidates() {
        ClassNode source = classWithMembers("source/A", "I", "()V");
        ClassNode exact = classWithMembers("target/Exact", "I", "()V");
        ClassNode partial = classWithMembers("target/Partial", "I", null);

        List<ClassMatch> matches = service.findCandidates(List.of(partial, exact), source, 60, 10);

        assertEquals(List.of(exact, partial), matches.stream().map(ClassMatch::classNode).toList());
        assertEquals(100, matches.get(0).similarityPercent());
    }

    @Test
    void enforcesOneToOneMappingsAndTracksRejections() {
        ClassNode firstSource = classWithMembers("source/A", "I", "()V");
        ClassNode secondSource = classWithMembers("source/B", "I", "()V");
        ClassNode target = classWithMembers("target/Z", "I", "()V");
        ClassMatch candidate = new ClassMatch(target, 100);

        service.accept("old.jar", "new.jar", firstSource, candidate);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.accept("old.jar", "new.jar", secondSource, candidate)
        );
        assertEquals("target/Z", service.getAcceptedMapping("old.jar", "new.jar", firstSource)
            .orElseThrow()
            .targetClassName());

        service.reject("old.jar", "new.jar", firstSource, target);

        assertTrue(service.isRejected("old.jar", "new.jar", firstSource, target));
        assertTrue(service.getAcceptedMapping("old.jar", "new.jar", firstSource).isEmpty());
        service.clearSourceDecisions("old.jar", "new.jar", firstSource);
        assertFalse(service.isRejected("old.jar", "new.jar", firstSource, target));
    }

    @Test
    void autoAcceptsOnlyUniqueBestCandidates() {
        ClassNode uniqueSource = classWithMembers("source/Unique", "I", "()V");
        ClassNode ambiguousSource = classWithMembers("source/Ambiguous", "J", null);
        ClassNode uniqueTarget = classWithMembers("target/Unique", "I", "()V");
        ClassNode ambiguousTargetOne = classWithMembers("target/AmbiguousOne", "J", null);
        ClassNode ambiguousTargetTwo = classWithMembers("target/AmbiguousTwo", "J", null);

        List<ClassMappingDecision> mappings = service.autoAcceptUniqueMatches(
            "old.jar",
            "new.jar",
            List.of(ambiguousSource, uniqueSource),
            List.of(ambiguousTargetTwo, uniqueTarget, ambiguousTargetOne),
            50
        );

        assertEquals(1, mappings.size());
        assertEquals("source/Unique", mappings.get(0).sourceClassName());
        assertEquals("target/Unique", mappings.get(0).targetClassName());
    }

    @Test
    void exportsAcceptedMappingsAsJsonAndClearsReloadedJarState(@TempDir Path tempDirectory) throws Exception {
        ClassNode source = classWithMembers("source/Quote\"Class", "I", "()V");
        ClassNode target = classWithMembers("target/Back\\slash", "I", "()V");
        service.accept("old\".jar", "new.jar", source, new ClassMatch(target, 100));
        Path destination = tempDirectory.resolve("mappings.json");

        service.exportAcceptedMappings(destination.toFile(), "old\".jar", "new.jar");
        String json = Files.readString(destination);

        assertTrue(json.contains("\"sourceJar\": \"old\\\".jar\""));
        assertTrue(json.contains("\"source\": \"source/Quote\\\"Class\""));
        assertTrue(json.contains("\"target\": \"target/Back\\\\slash\""));

        service.clearJar("new.jar");
        assertTrue(service.getAcceptedMappings("old\".jar", "new.jar").isEmpty());
    }

    private ClassNode classWithMembers(String name, String fieldDescriptor, String methodDescriptor) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        if (fieldDescriptor != null) {
            classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "field", fieldDescriptor, null, null));
        }
        if (methodDescriptor != null) {
            classNode.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "method", methodDescriptor, null, null));
        }
        return classNode;
    }
}
