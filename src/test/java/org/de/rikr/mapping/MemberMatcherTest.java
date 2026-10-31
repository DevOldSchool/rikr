package org.de.rikr.mapping;

import org.de.rikr.mapping.MemberMatch.MemberRef;
import org.de.rikr.mapping.MemberMatch.Reason;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberMatcherTest {
    private final MemberMatcher matcher = new MemberMatcher();

    @Test
    void matchesInstanceMethodsOfMatchedClassesByWhatTheyDo() {
        ClassNode source = classNode("a");
        source.methods.add(returning("greet", "hello"));
        source.methods.add(returning("leave", "goodbye"));
        ClassNode target = classNode("b");
        target.methods.add(returning("x", "goodbye"));
        target.methods.add(returning("y", "hello"));

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals("y", method(matches, "greet").orElseThrow().target().name());
        assertEquals("x", method(matches, "leave").orElseThrow().target().name());
        assertEquals(Reason.SAME_BEHAVIOUR, method(matches, "greet").orElseThrow().reason());
        assertEquals(100, method(matches, "greet").orElseThrow().confidencePercent());
        assertEquals(2, matches.sourceMethods());
    }

    @Test
    void matchesStaticMethodsThatMovedToAnotherClass() {
        ClassNode source = classNode("a");
        source.methods.add(staticReturning("one", "first"));
        source.methods.add(staticReturning("two", "second"));
        ClassNode target = classNode("b");
        ClassNode elsewhere = classNode("c");
        elsewhere.methods.add(staticReturning("p", "second"));
        elsewhere.methods.add(staticReturning("q", "first"));

        MemberMatches matches = matcher.match(List.of(source), List.of(target, elsewhere), Map.of("a", "b"));

        assertEquals(new MemberRef("c", "q", "()Ljava/lang/String;"), method(matches, "one").orElseThrow().target());
        assertEquals(new MemberRef("c", "p", "()Ljava/lang/String;"), method(matches, "two").orElseThrow().target());
    }

    @Test
    void leavesTiedMethodsUnmatchedRatherThanGuessing() {
        ClassNode source = classNode("a");
        source.methods.add(returning("m1", "same"));
        source.methods.add(returning("m2", "same"));
        ClassNode target = classNode("b");
        target.methods.add(returning("n1", "same"));
        target.methods.add(returning("n2", "same"));

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals(List.of(), matches.methods());
    }

    @Test
    void tellsAnUnusedCopyFromTheMethodItCopiesByItsCallers() {
        ClassNode source = classNode("a");
        source.methods.add(returning("real", "same"));
        source.methods.add(returning("copy", "same"));
        source.methods.add(calling("a", "main", "real"));
        ClassNode target = classNode("b");
        target.methods.add(returning("junk", "same"));
        target.methods.add(returning("used", "same"));
        target.methods.add(calling("b", "start", "used"));

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals("start", method(matches, "main").orElseThrow().target().name());
        assertEquals("used", method(matches, "real").orElseThrow().target().name());
        assertEquals("junk", method(matches, "copy").orElseThrow().target().name());
    }

    @Test
    void matchesMethodsWhoseUnusedLastParameterChangedType() {
        ClassNode source = classNode("a");
        source.methods.add(staticReturning("name", "(I)Ljava/lang/String;", "player"));
        source.methods.add(staticReturning("ints", "([I)Ljava/lang/String;", "array"));
        ClassNode target = classNode("b");
        target.methods.add(staticReturning("n", "(B)Ljava/lang/String;", "player"));
        target.methods.add(staticReturning("i", "(I)Ljava/lang/String;", "array"));

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals("n", method(matches, "name").orElseThrow().target().name());
        // An int array is a real parameter, so it is not matched to a method that takes an int.
        assertTrue(method(matches, "ints").isEmpty());
    }

    @Test
    void matchesFieldsByWhereTheirValuesComeFromAndGo() {
        ClassNode source = point("a", "x", "y");
        ClassNode target = point("b", "q", "p");

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals("q", field(matches, "x").orElseThrow().target().name());
        assertEquals("p", field(matches, "y").orElseThrow().target().name());
        assertEquals(Reason.SIMILAR_USE, field(matches, "x").orElseThrow().reason());
        assertEquals(3, matches.sourceFields());
    }

    @Test
    void matchesFieldsUsedAlikeByTheOrderTheConstructorSetsThem() {
        ClassNode source = initializedInOrder("a", "first", "second", "third");
        ClassNode target = initializedInOrder("b", "p", "q", "r");
        // Declarations are shuffled, as obfuscators and deobfuscators do; the constructor's order is not.
        target.fields.addFirst(target.fields.removeLast());

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals("p", field(matches, "first").orElseThrow().target().name());
        assertEquals("q", field(matches, "second").orElseThrow().target().name());
        assertEquals("r", field(matches, "third").orElseThrow().target().name());
    }

    @Test
    void theOnlyCandidateMatchesWithoutEvidence() {
        ClassNode source = classNode("a");
        source.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "unused", "J", null, null));
        source.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "()V", null, null));
        ClassNode target = classNode("b");
        target.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "u", "J", null, null));
        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "r", "()V", null, null));

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        MemberMatch run = method(matches, "run").orElseThrow();
        assertEquals(Reason.ONLY_CANDIDATE, run.reason());
        assertEquals(MemberMatcher.NO_EVIDENCE_PERCENT, run.confidencePercent());
        assertEquals("u", field(matches, "unused").orElseThrow().target().name());
    }

    @Test
    void ignoresInstanceMembersOfUnmatchedClassesAndMatchesConstructorsOnlyToConstructors() {
        ClassNode unmatched = classNode("z");
        unmatched.methods.add(returning("m", "text"));
        ClassNode source = classNode("a");
        source.methods.add(constructor());
        ClassNode target = classNode("b");
        target.methods.add(constructor());
        MethodNode lookalike = constructor();
        lookalike.name = "init";
        target.methods.add(lookalike);

        MemberMatches matches = matcher.match(List.of(unmatched, source), List.of(target), Map.of("a", "b"));

        assertEquals(1, matches.sourceMethods());
        assertEquals("<init>", method(matches, "<init>").orElseThrow().target().name());
    }

    @Test
    void comparesEveryKindOfInstruction() {
        ClassNode source = classNode("a");
        source.methods.add(everything("a", "busy"));
        source.methods.add(everything("a", "busy2"));
        source.methods.get(1).instructions.insert(new LdcInsnNode("different"));
        ClassNode target = classNode("b");
        target.methods.add(everything("b", "x"));

        MemberMatches matches = matcher.match(List.of(source), List.of(target), Map.of("a", "b"));

        assertEquals("x", method(matches, "busy").orElseThrow().target().name());
        assertTrue(method(matches, "busy2").isEmpty());
    }

    @Test
    void diceIsTheShareOfFeaturesInCommon() {
        assertEquals(-1, MemberMatcher.dice(Map.of(), Map.of("a", 1)));
        assertEquals(100, MemberMatcher.dice(Map.of("a", 2), Map.of("a", 2)));
        assertEquals(50, MemberMatcher.dice(Map.of("a", 1, "b", 1), Map.of("a", 1, "c", 1)));
    }

    @Test
    void describesMatches() {
        MemberRef field = new MemberRef("a", "f", "I");
        MemberRef method = new MemberRef("a", "m", "()V");

        assertEquals("a.f:I", field.toString());
        assertEquals("a.m()V", method.toString());
        assertEquals("similar code", Reason.SIMILAR_CODE.description());
        assertThrows(IllegalArgumentException.class, () -> new MemberMatch(field, field, 101, Reason.SIMILAR_USE));
    }

    private static Optional<MemberMatch> method(MemberMatches matches, String sourceName) {
        return matches.methods().stream().filter(match -> match.source().name().equals(sourceName)).findFirst();
    }

    private static Optional<MemberMatch> field(MemberMatches matches, String sourceName) {
        return matches.fields().stream().filter(match -> match.source().name().equals(sourceName)).findFirst();
    }

    private static MethodNode returning(String name, String text) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()Ljava/lang/String;", null, null);
        method.instructions.add(new LdcInsnNode(text));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    private static MethodNode staticReturning(String name, String text) {
        return staticReturning(name, "()Ljava/lang/String;", text);
    }

    private static MethodNode staticReturning(String name, String descriptor, String text) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
        method.instructions.add(new LdcInsnNode(text));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    // A method of its own shape that calls another, so that it matches by itself and then vouches for its callee.
    private static MethodNode calling(String owner, String name, String callee) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "(J)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, callee, "()Ljava/lang/String;"));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static MethodNode constructor() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    /**
     * A class with int fields set from a constructor's two parameters, each multiplied by a constant as an
     * obfuscator would, and a getter for the first, plus a field the constructor sets to a constant.
     */
    private static ClassNode point(String name, String first, String second) {
        ClassNode classNode = classNode(name);
        // Declared second-first, so matching can't rely on order.
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, second, "I", null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, first, "I", null, null));
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "LIMIT", "I", null, 7));
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(II)V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        constructor.instructions.add(new LdcInsnNode(name.equals("a") ? 31 : 97));
        constructor.instructions.add(new InsnNode(Opcodes.IMUL));
        constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, name, first, "I"));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new LdcInsnNode(name.equals("a") ? 17 : 53));
        constructor.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        constructor.instructions.add(new InsnNode(Opcodes.IMUL));
        constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, name, second, "I"));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        classNode.methods.add(constructor);
        MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC, "get", "()I", null, null);
        getter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, name, first, "I"));
        getter.instructions.add(new LdcInsnNode(name.equals("a") ? 5 : 11));
        getter.instructions.add(new InsnNode(Opcodes.IMUL));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));
        classNode.methods.add(getter);
        return classNode;
    }

    // A class whose constructor sets each of its int fields to zero, in the order given.
    private static ClassNode initializedInOrder(String name, String... fields) {
        ClassNode classNode = classNode(name);
        MethodNode constructor = constructor();
        constructor.instructions.remove(constructor.instructions.getLast());
        for (String field : fields) {
            classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, field, "I", null, null));
            constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            constructor.instructions.add(new InsnNode(Opcodes.ICONST_0));
            constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, name, field, "I"));
        }
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        classNode.methods.add(constructor);
        return classNode;
    }

    private static MethodNode everything(String owner, String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()V", null, null);
        LabelNode end = new LabelNode();
        method.instructions.add(new LdcInsnNode(Type.getObjectType(owner)));
        method.instructions.add(new LdcInsnNode(1.5f));
        method.instructions.add(new LdcInsnNode(2.5d));
        method.instructions.add(new LdcInsnNode(123_456_789L));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 12));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "[I"));
        method.instructions.add(new MultiANewArrayInsnNode("[[L" + owner + ";", 2));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out",
            "Ljava/io/PrintStream;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "flush", "()V"));
        method.instructions.add(new InvokeDynamicInsnNode("run", "()Ljava/lang/Runnable;", new Handle(
            Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false)));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, end));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
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
