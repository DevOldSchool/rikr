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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenameServiceTest {
    @Test
    void returnsCountsForClassFieldAndMethodRenames() {
        ClassNode owner = classNode("a/Owner");
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "oldField", "I", null, null));
        owner.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "oldMethod", "()V", null, null));

        ClassNode caller = classNode("a/Caller");
        MethodNode callerMethod = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
        callerMethod.instructions = new InsnList();
        callerMethod.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "a/Owner", "oldField", "I"));
        callerMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a/Owner", "oldMethod", "()V", false));
        caller.methods.add(callerMethod);

        RenameService service = new RenameService();
        RenameResult fieldResult = service.renameFieldReferences(List.of(owner, caller), owner, "oldField", "newField");
        RenameResult methodResult = service.renameMethodReferences(List.of(owner, caller), owner, "oldMethod", "newMethod");
        RenameResult classResult = service.renameClassReferences(List.of(owner, caller), "a/Owner", "a/RenamedOwner");

        assertEquals(new RenameResult("field", 2, 2), fieldResult);
        assertEquals(new RenameResult("method", 2, 2), methodResult);
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
    void rejectsMethodFamilyCollisionAcrossOverloads() {
        ClassNode owner = classNode("a/Owner");
        MethodNode selected = new MethodNode(Opcodes.ACC_PUBLIC, "old", "()V", null, null);
        owner.methods.add(selected);
        owner.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "old", "(I)V", null, null));
        owner.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "target", "(I)V", null, null));

        RenamePreview preview = new RenameService().previewMethodRename(
            List.of(owner),
            owner,
            selected,
            "target"
        );

        assertFalse(preview.valid());
        assertTrue(preview.validationMessage().contains("collide"));
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
