package org.de.rikr.mapping;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassVotingTest {
    private final ClassVoting voting = new ClassVoting();

    @Test
    void matchesTheClassesThatMatchedMembersAreTyped() {
        // a holds a b and returns it; x holds a y and returns it. a is known to be x.
        List<ClassNode> sources = List.of(holder("a", "b"), classNode("b"));
        List<ClassNode> targets = List.of(holder("x", "y"), classNode("y"));

        List<ClassVoting.VotedMatch> found = voting.match(sources, targets, Map.of("a", "x"));

        assertEquals(List.of(new ClassVoting.VotedMatch("b", "y", 2, 0)), found);
        assertEquals(100, found.getFirst().confidencePercent());
    }

    @Test
    void followsSuperclassesOfMatchedClassesAndKeepsGoing() {
        // c extends a, which holds a b. Matching c first gives a by its superclass, then a's members give b.
        ClassNode c = classNode("c");
        c.superName = "a";
        ClassNode z = classNode("z");
        z.superName = "x";
        List<ClassNode> sources = List.of(holder("a", "b"), classNode("b"), c);
        List<ClassNode> targets = List.of(holder("x", "y"), classNode("y"), z);

        List<ClassVoting.VotedMatch> found = voting.match(sources, targets, Map.of("c", "z"));

        assertEquals(List.of("a", "b"), found.stream().map(ClassVoting.VotedMatch::source).toList());
    }

    @Test
    void leavesUnclearVotesAndMismatchedKindsUnmatched() {
        // a's members point at b; x's point at y and w equally, so there is no clear leader.
        ClassNode a = holder("a", "b");
        ClassNode x = classNode("x");
        x.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "f", "Ly;", null, null));
        x.methods.add(getter("x", "f", "w"));
        ClassNode interfaceY = classNode("y");
        interfaceY.access |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;

        assertEquals(List.of(), voting.match(List.of(a, classNode("b")), List.of(x, interfaceY, classNode("w")),
            Map.of("a", "x")));
        // With one clear target that is an interface while b is a class, nothing is matched either.
        assertEquals(List.of(), voting.match(List.of(holder("a", "b"), classNode("b")),
            List.of(holder("x", "y"), interfaceY), Map.of("a", "x")));
    }

    // A class with a field of the other type and a getter that returns it.
    private static ClassNode holder(String name, String held) {
        ClassNode classNode = classNode(name);
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "f", "L" + held + ";", null, null));
        classNode.methods.add(getter(name, "f", held));
        return classNode;
    }

    private static MethodNode getter(String owner, String field, String type) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "get", "()L" + type + ";", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, field, "L" + type + ";"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        return classNode;
    }
}
