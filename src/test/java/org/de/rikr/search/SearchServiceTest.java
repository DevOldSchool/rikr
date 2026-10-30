package org.de.rikr.search;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {
    @Test
    void findsClassMembersInstructionsAndAnnotations() {
        ClassNode classNode = sampleClass();
        SearchService searchService = new SearchService();

        List<SearchResult> fieldResults = searchService.search(Map.of("sample.jar", List.of(classNode)), query("targetField"));
        List<SearchResult> methodResults = searchService.search(Map.of("sample.jar", List.of(classNode)), query("targetMethod"));
        List<SearchResult> instructionResults = searchService.search(Map.of("sample.jar", List.of(classNode)), query("needle"));
        List<SearchResult> annotationResults = searchService.search(Map.of("sample.jar", List.of(classNode)), query("marker"));

        assertTrue(fieldResults.stream().anyMatch(result -> result.icon() == SearchResultIcon.FIELD));
        assertTrue(methodResults.stream().anyMatch(result -> result.icon() == SearchResultIcon.METHOD));
        assertTrue(instructionResults.stream().anyMatch(result -> result.icon() == SearchResultIcon.INSTRUCTION));
        assertTrue(annotationResults.stream().anyMatch(result -> "@Example".equals(result.text())));
    }

    private SearchQuery query(String text) {
        return new SearchQuery(text, false, false);
    }

    private ClassNode sampleClass() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "fixture/Sample";
        classNode.superName = "java/lang/Object";
        classNode.interfaces = new ArrayList<>();

        AnnotationNode annotationNode = new AnnotationNode("Lfixture/Example;");
        annotationNode.values = List.of("value", "marker");
        classNode.visibleAnnotations = List.of(annotationNode);

        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "targetField", "Ljava/lang/String;", null, null));

        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC, "targetMethod", "()V", null, null);
        methodNode.instructions = new InsnList();
        methodNode.instructions.add(new LdcInsnNode("needle"));
        methodNode.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        classNode.methods.add(methodNode);

        return classNode;
    }
}
