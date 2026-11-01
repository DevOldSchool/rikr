package org.de.rikr.mapping;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberAlignmentTest {

    @Test
    void pairsMembersOfTheSameShapeWhateverTheirNames() {
        ClassNode source = classNode("bm");
        source.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "a", "I", null, null));
        source.fields.add(new FieldNode(Opcodes.ACC_STATIC, "b", "Lcy;", null, null));
        source.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "c", "(ILjava/lang/String;)V", null, null));
        ClassNode target = classNode("af");
        target.fields.add(new FieldNode(Opcodes.ACC_STATIC, "x", "Lzz;", null, null));
        target.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "y", "I", null, null));
        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "z", "(ILjava/lang/String;)V", null, null));

        List<MemberAlignment.Row> rows = MemberAlignment.align(source, target);

        assertEquals(List.of(
            new MemberAlignment.Row(MemberAlignment.Section.FIELDS, "int a", "int y"),
            new MemberAlignment.Row(MemberAlignment.Section.FIELDS, "static cy b", "static zz x"),
            new MemberAlignment.Row(MemberAlignment.Section.METHODS, "void c(int, String)", "void z(int, String)")
        ), rows);
        assertTrue(rows.stream().allMatch(MemberAlignment.Row::matched));
    }

    @Test
    void putsMembersWithoutAPartnerOnRowsOfTheirOwn() {
        ClassNode source = classNode("bm");
        source.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "a", "I", null, null));
        source.methods.add(new MethodNode(Opcodes.ACC_STATIC, "run", "()[[J", null, null));
        ClassNode target = classNode("af");
        target.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "b", "J", null, null));
        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "run", "()[[J", null, null));

        List<MemberAlignment.Row> rows = MemberAlignment.align(source, target);

        assertEquals(List.of(
            new MemberAlignment.Row(MemberAlignment.Section.FIELDS, "int a", null),
            new MemberAlignment.Row(MemberAlignment.Section.FIELDS, null, "long b"),
            new MemberAlignment.Row(MemberAlignment.Section.METHODS, "static long[][] run()", null),
            new MemberAlignment.Row(MemberAlignment.Section.METHODS, null, "long[][] run()")
        ), rows);
        assertFalse(rows.getFirst().matched());
    }

    @Test
    void listsTheSourceAloneWithoutATarget() {
        ClassNode source = classNode("bm");
        source.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null));

        assertEquals(List.of(new MemberAlignment.Row(MemberAlignment.Section.METHODS, "void <init>()", null)),
            MemberAlignment.align(source, null));
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        return classNode;
    }
}
