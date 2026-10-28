package org.de.rikr.service;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameServiceTest {
    @Test
    void returnsCountsForClassFieldAndMethodRenames() {
        ClassNode owner = classNode("a/Owner");
        FieldNode field = new FieldNode(Opcodes.ACC_PUBLIC, "oldField", "I", null, null);
        owner.fields.add(field);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "oldMethod", "()V", null, null);
        owner.methods.add(method);

        ClassNode caller = classNode("a/Caller");
        MethodNode callerMethod = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
        callerMethod.instructions = new InsnList();
        callerMethod.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "a/Owner", "oldField", "I"));
        callerMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a/Owner", "oldMethod", "()V", false));
        caller.methods.add(callerMethod);

        List<ClassNode> classes = List.of(owner, caller);
        RenameService service = new RenameService();
        RenameResult fieldResult = service.applyFieldRename("app.jar", classes, owner, field, "newField");
        RenameResult methodResult = service.applyMethodRename("app.jar", classes, owner, method, "newMethod");
        RenameResult classResult = service.applyClassRename("app.jar", classes, owner, "a/RenamedOwner");

        assertEquals(new RenameResult("field", 2, 2, Set.of("a/Owner")), fieldResult);
        assertEquals(new RenameResult("method", 2, 2, Set.of("a/Owner")), methodResult);
        assertEquals("a/RenamedOwner", owner.name);
        assertEquals(3, classResult.referenceCount());
    }

    @Test
    void previewsWithoutMutatingAndRejectsCollisions() {
        ClassNode owner = classNode("a/Owner");
        FieldNode field = new FieldNode(Opcodes.ACC_PUBLIC, "oldField", "I", null, null);
        owner.fields.add(field);
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "taken", "I", null, null));
        ClassNode caller = classNode("a/Caller");
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "a/Owner", "oldField", "I"));
        caller.methods.add(method);

        RenameService service = new RenameService();
        RenamePreview preview = service.previewFieldRename(List.of(owner, caller), owner, field, "renamed");
        RenamePreview collision = service.previewFieldRename(List.of(owner, caller), owner, field, "taken");

        assertTrue(preview.valid());
        assertEquals(2, preview.referenceCount());
        assertEquals(List.of("a/Owner", "a/Caller"), preview.affectedClasses());
        assertEquals("oldField", field.name);
        assertFalse(collision.valid());
        assertTrue(collision.validationMessage().contains("already exists"));
    }

    @Test
    void appliesTracksAndUndoesRename() {
        ClassNode owner = classNode("a/Owner");
        FieldNode field = new FieldNode(Opcodes.ACC_PUBLIC, "oldField", "I", null, null);
        owner.fields.add(field);
        List<ClassNode> classes = List.of(owner);
        RenameService service = new RenameService();

        service.applyFieldRename("app.jar", classes, owner, field, "newField");

        assertEquals("newField", field.name);
        assertTrue(service.getDirtyJars().contains("app.jar"));
        assertEquals(1, service.getHistory().size());

        RenameUndoResult undo = service.undoLast(jarName -> classes).orElseThrow();

        assertEquals("oldField", field.name);
        assertEquals(RenameType.FIELD, undo.operation().type());
        assertFalse(service.canUndo());
        assertTrue(service.getDirtyJars().contains("app.jar"));
    }

    @Test
    void renamesOneOverloadAndRejectsSameDescriptorCollision() {
        ClassNode owner = classNode("a/Owner");
        MethodNode selected = new MethodNode(Opcodes.ACC_PUBLIC, "old", "()V", null, null);
        MethodNode overload = new MethodNode(Opcodes.ACC_PUBLIC, "old", "(I)V", null, null);
        owner.methods.add(selected);
        owner.methods.add(overload);
        owner.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "taken", "()V", null, null));
        RenameService service = new RenameService();

        RenamePreview collision = service.previewMethodRename(List.of(owner), owner, selected, "taken");
        service.applyMethodRename("app.jar", List.of(owner), owner, selected, "renamed");

        assertFalse(collision.valid());
        assertTrue(collision.validationMessage().contains("collide"));
        assertEquals("renamed", selected.name);
        assertEquals("old", overload.name);
    }

    @Test
    void undoOnlyRevertsTheRenamedMethodWhenTheNameIsShared() {
        ClassNode owner = classNode("a/Owner");
        MethodNode renamed = new MethodNode(Opcodes.ACC_PUBLIC, "m", "(I)V", null, null);
        MethodNode existing = new MethodNode(Opcodes.ACC_PUBLIC, "foo", "(J)V", null, null);
        owner.methods.add(renamed);
        owner.methods.add(existing);
        ClassNode caller = classNode("a/Caller");
        MethodNode call = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
        call.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a/Owner", "foo", "(J)V", false));
        caller.methods.add(call);
        List<ClassNode> classes = List.of(owner, caller);
        RenameService service = new RenameService();

        service.applyMethodRename("app.jar", classes, owner, renamed, "foo");
        service.undoLast(jarName -> classes).orElseThrow();

        assertEquals("m", renamed.name);
        assertEquals("foo", existing.name);
        assertEquals("foo", firstCall(call).name);
    }

    @Test
    void renamesOverridesAndCallsThroughSubclasses() {
        ClassNode base = classNode("a/Base");
        MethodNode baseMethod = new MethodNode(Opcodes.ACC_PUBLIC, "m", "()V", null, null);
        base.methods.add(baseMethod);
        ClassNode child = classNode("a/Child");
        child.superName = "a/Base";
        MethodNode override = new MethodNode(Opcodes.ACC_PUBLIC, "m", "()V", null, null);
        child.methods.add(override);
        ClassNode grandChild = classNode("a/GrandChild");
        grandChild.superName = "a/Child";
        ClassNode caller = classNode("a/Caller");
        MethodNode call = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
        call.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a/GrandChild", "m", "()V", false));
        caller.methods.add(call);

        RenameResult result = new RenameService().applyMethodRename(
            "app.jar", List.of(base, child, grandChild, caller), base, baseMethod, "renamed"
        );

        assertEquals("renamed", override.name);
        assertEquals("renamed", firstCall(call).name);
        assertEquals(Set.of("a/Base", "a/Child"), result.declarationOwners());
    }

    @Test
    void rejectsRenamingLibraryOverridesAndCreatingNewOverrides() {
        ClassNode owner = classNode("a/Owner");
        MethodNode toString = new MethodNode(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        MethodNode describe = new MethodNode(Opcodes.ACC_PUBLIC, "describe", "()Ljava/lang/String;", null, null);
        owner.methods.add(toString);
        owner.methods.add(describe);
        RenameService service = new RenameService();

        RenamePreview library = service.previewMethodRename(List.of(owner), owner, toString, "x");
        RenamePreview accidental = service.previewMethodRename(List.of(owner), owner, describe, "toString");

        assertFalse(library.valid());
        assertTrue(library.validationMessage().contains("java/lang/Object"));
        assertFalse(accidental.valid());
    }

    @Test
    void rejectsFieldRenameThatWouldBeShadowedBySubclass() {
        ClassNode base = classNode("a/Base");
        FieldNode field = new FieldNode(Opcodes.ACC_PUBLIC, "f", "I", null, null);
        base.fields.add(field);
        ClassNode child = classNode("a/Child");
        child.superName = "a/Base";
        child.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "g", "I", null, null));

        RenamePreview preview = new RenameService().previewFieldRename(List.of(base, child), base, field, "g");

        assertFalse(preview.valid());
        assertTrue(preview.validationMessage().contains("a/Child"));
    }

    // Renames replace instruction nodes, so look them up again afterwards.
    private MethodInsnNode firstCall(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                return call;
            }
        }
        throw new AssertionError("No call in " + method.name);
    }

    private ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        return classNode;
    }
}
