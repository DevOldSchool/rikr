package org.de.rikr.service;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceIndexServiceTest {
    private final ReferenceIndexService service = new ReferenceIndexService();

    @Test
    void indexesCallersCalleesAndFieldAccesses() {
        ClassNode target = createClass("target/Base", "java/lang/Object");
        FieldNode targetField = new FieldNode(Opcodes.ACC_PUBLIC, "value", "I", null, null);
        MethodNode targetMethod = method("work", "()V");
        target.fields.add(targetField);
        target.methods.add(targetMethod);

        ClassNode caller = createClass("source/Caller", "java/lang/Object");
        MethodNode callerMethod = method("call", "()V");
        callerMethod.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, target.name, targetField.name, targetField.desc));
        callerMethod.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, target.name, targetField.name, targetField.desc));
        callerMethod.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            target.name,
            targetMethod.name,
            targetMethod.desc,
            false
        ));
        callerMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        caller.methods.add(callerMethod);

        service.indexJar("fixture.jar", List.of(caller, target));

        List<ReferenceResult> reads = service.findFieldReads("fixture.jar", target, targetField);
        List<ReferenceResult> writes = service.findFieldWrites("fixture.jar", target, targetField);
        List<ReferenceResult> callers = service.findMethodCallers("fixture.jar", target, targetMethod);
        List<ReferenceResult> callees = service.findMethodCallees("fixture.jar", caller, callerMethod);

        assertEquals(1, reads.size());
        assertEquals(ReferenceKind.FIELD_READ, reads.get(0).kind());
        assertEquals(1, writes.size());
        assertEquals(ReferenceKind.FIELD_WRITE, writes.get(0).kind());
        assertEquals(1, callers.size());
        assertSame(caller, callers.get(0).navigationClass());
        assertEquals(1, callees.size());
        assertSame(target, callees.get(0).navigationClass());
    }

    @Test
    void indexesInheritanceDescriptorsAndInstructionTypes() {
        ClassNode contract = createClass("target/Contract", "java/lang/Object");
        contract.access |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        ClassNode target = createClass("target/Base", "java/lang/Object");
        ClassNode caller = createClass("source/Caller", target.name);
        caller.interfaces.add(contract.name);
        caller.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "base", "Ltarget/Base;", null, null));

        MethodNode method = method("convert", "(Ltarget/Base;)Ltarget/Base;");
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, target.name));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, target.name));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        caller.methods.add(method);

        service.indexJar("fixture.jar", List.of(contract, caller, target));

        List<ReferenceKind> targetKinds = service.findTypeReferences("fixture.jar", target).stream()
            .map(ReferenceResult::kind)
            .toList();
        List<ReferenceKind> contractKinds = service.findTypeReferences("fixture.jar", contract).stream()
            .map(ReferenceResult::kind)
            .toList();

        assertTrue(targetKinds.contains(ReferenceKind.EXTENDS));
        assertTrue(targetKinds.contains(ReferenceKind.FIELD_TYPE));
        assertTrue(targetKinds.contains(ReferenceKind.METHOD_TYPE));
        assertTrue(targetKinds.contains(ReferenceKind.INSTRUCTION_TYPE));
        assertTrue(contractKinds.contains(ReferenceKind.IMPLEMENTS));
    }

    @Test
    void marksExternalCalleesAsNonNavigableAndClearsRemovedIndexes() {
        ClassNode caller = createClass("source/Caller", "java/lang/Object");
        MethodNode method = method("callExternal", "()V");
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "java/lang/String",
            "valueOf",
            "(I)Ljava/lang/String;",
            false
        ));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        caller.methods.add(method);
        service.indexJar("fixture.jar", List.of(caller));

        List<ReferenceResult> callees = service.findMethodCallees("fixture.jar", caller, method);

        assertEquals(1, callees.size());
        assertFalse(callees.get(0).isNavigable());
        service.removeJar("fixture.jar");
        assertTrue(service.findMethodCallees("fixture.jar", caller, method).isEmpty());
    }

    private ClassNode createClass(String name, String superName) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = superName;
        return classNode;
    }

    private MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
    }
}
