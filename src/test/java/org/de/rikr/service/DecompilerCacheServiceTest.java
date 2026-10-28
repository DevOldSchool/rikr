package org.de.rikr.service;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerCacheServiceTest {
    @Test
    void returnsStructuredFailureForMissingClass() {
        try (DecompilerCacheService service = new DecompilerCacheService(message -> {
        })) {
            DecompileResult result = service.decompileClassAsync(null).join();

            assertFalse(result.success());
            assertNotNull(result.message());
        }
    }

    @Test
    void cachesPerClassInstanceAndDropsSourcesOnInvalidation() {
        ClassNode first = simpleClass("a");
        ClassNode sameNameInOtherJar = simpleClass("a");
        try (DecompilerCacheService service = new DecompilerCacheService(message -> {
        })) {
            assertTrue(service.decompileClassAsync(first).join().success());

            assertNotNull(service.getDecompiledSource(first));
            assertNull(service.getDecompiledSource(sameNameInOtherJar));

            service.invalidateJar("first.jar", List.of(first));

            assertNull(service.getDecompiledSource(first));
        }
    }

    private ClassNode simpleClass(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        classNode.methods.add(method);
        return classNode;
    }
}
