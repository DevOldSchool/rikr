package org.de.rikr.behavioral;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassComparatorTest {
    private final ClassComparator comparator = new ClassComparator();

    @Test
    void exactMatchIgnoresMemberOrderAndObfuscatedTypeNames() {
        ClassNode source = createClass("old/A", "old/Base");
        source.fields.add(new FieldNode(0, "first", "I", null, null));
        source.fields.add(new FieldNode(0, "second", "Lold/Value;", null, null));
        source.methods.add(new MethodNode(0, "run", "(Lold/Input;)V", null, null));
        source.methods.add(new MethodNode(0, "value", "()Ljava/lang/String;", null, null));

        ClassNode candidate = createClass("new/Z", "new/Parent");
        candidate.fields.add(new FieldNode(0, "b", "Lnew/Thing;", null, null));
        candidate.fields.add(new FieldNode(0, "a", "I", null, null));
        candidate.methods.add(new MethodNode(0, "b", "()Ljava/lang/String;", null, null));
        candidate.methods.add(new MethodNode(0, "a", "(Lnew/Argument;)V", null, null));

        assertEquals(100, comparator.getSimilarityPercent(source, candidate));
        assertEquals(List.of(candidate), comparator.getMatchingClassNodes(List.of(candidate), source));
    }

    @Test
    void ranksPartialMatchesAndRejectsDifferentClassKinds() {
        ClassNode source = createClass("old/A", "java/lang/Object");
        source.fields.add(new FieldNode(0, "value", "I", null, null));
        source.methods.add(new MethodNode(0, "run", "()V", null, null));

        ClassNode closeMatch = createClass("new/B", "java/lang/Object");
        closeMatch.fields.add(new FieldNode(0, "renamed", "I", null, null));
        closeMatch.methods.add(new MethodNode(0, "renamed", "()V", null, null));
        closeMatch.methods.add(new MethodNode(0, "added", "(I)V", null, null));

        ClassNode weakMatch = createClass("new/C", "new/Parent");
        ClassNode interfaceNode = createClass("new/I", "java/lang/Object");
        interfaceNode.access |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;

        assertTrue(comparator.getSimilarityPercent(source, closeMatch)
            > comparator.getSimilarityPercent(source, weakMatch));
        assertEquals(0, comparator.getSimilarityPercent(source, interfaceNode));
        assertEquals(closeMatch, comparator.getClassNodeMatches(List.of(weakMatch, closeMatch), source).get(0).classNode());
    }

    @Test
    void repeatedPartsMatchOncePerCopyAndSignaturesGiveTheSameMatches() {
        ClassNode source = createClass("old/A", "java/lang/Object");
        source.fields.add(new FieldNode(0, "x", "I", null, null));
        source.fields.add(new FieldNode(0, "y", "I", null, null));
        ClassNode candidate = createClass("new/B", "java/lang/Object");
        candidate.fields.add(new FieldNode(0, "a", "I", null, null));

        // Three of the source's four parts and all three of the candidate's: 200 * 3 / 7.
        assertEquals(86, comparator.getSimilarityPercent(source, candidate));
        List<ClassNode> candidates = List.of(candidate, source);
        assertEquals(comparator.getClassNodeMatches(candidates, source),
            comparator.getClassNodeMatches(comparator.signatures(candidates), comparator.signature(source)));
    }

    @Test
    void handlesRootClassWithoutSuperclass() {
        ClassNode source = createClass("java/lang/Object", null);
        ClassNode candidate = createClass("java/lang/Object", null);

        assertEquals(100, comparator.getSimilarityPercent(source, candidate));
    }

    private ClassNode createClass(String name, String superName) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = superName;
        return classNode;
    }
}
