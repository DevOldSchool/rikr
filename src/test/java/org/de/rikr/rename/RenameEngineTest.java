package org.de.rikr.rename;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden tests: generate a small obfuscated-style program, rename it, then verify and run the result.
 */
class RenameEngineTest {
    private final RenameEngine engine = new RenameEngine();

    @Test
    void unrenamedProgramRuns() throws Exception {
        assertEquals(12, run(program()));
    }

    @Test
    void classRenameDoesNotTouchNamesThatOnlyContainTheOldName() {
        List<ClassNode> classes = program();
        ClassNode caller = find(classes, "c");

        engine.renameClass(classes, "a", "Foo", true);

        MethodNode take = ClassHierarchy.findMethod(caller, "take", "(LFoo;Lab;Ljava/lang/String;[LFoo;)V");
        assertNotNull(take, "descriptor should only rename the exact type a");
        assertEquals("Foo", find(classes, "ab").superName);
    }

    @Test
    void renamedProgramStillVerifiesAndDispatchesToOverrides() throws Exception {
        List<ClassNode> classes = program();
        ClassNode base = find(classes, "a");
        MethodNode baseMethod = ClassHierarchy.findMethod(base, "m", "()I");

        engine.renameClass(classes, "a", "Foo", true);
        engine.renameMethod(classes, "Foo", "m", "()I", "value", true);
        engine.renameField(classes, "Foo", "f", "I", "count", true);

        assertSame(baseMethod, ClassHierarchy.findMethod(base, "value", "()I"), "nodes keep their identity");
        assertNotNull(ClassHierarchy.findMethod(find(classes, "ab"), "value", "()I"), "override renamed with base");
        assertEquals(12, run(classes));
    }

    @Test
    void dryRunReportsChangesWithoutMutating() {
        List<ClassNode> classes = program();

        RenameEngine.Changes changes = engine.renameMethod(classes, "a", "m", "()I", "value", false);

        assertEquals(List.of("a", "ab", "c"), changes.affectedClasses().stream().sorted().toList());
        assertEquals(3, changes.referenceCount());
        assertNotNull(ClassHierarchy.findMethod(find(classes, "a"), "m", "()I"));
    }

    @Test
    void methodFamilyFollowsInterfacesImplementedThroughSubclasses() {
        ClassNode iface = classNode("i", "java/lang/Object", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT);
        iface.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "()V", null, null));
        ClassNode base = classNode("p", "java/lang/Object", Opcodes.ACC_PUBLIC);
        base.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null));
        ClassNode child = classNode("q", "p", Opcodes.ACC_PUBLIC);
        child.interfaces.add("i");
        ClassNode unrelated = classNode("r", "java/lang/Object", Opcodes.ACC_PUBLIC);
        unrelated.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null));

        ClassHierarchy hierarchy = new ClassHierarchy(List.of(iface, base, child, unrelated));

        assertEquals(Set.of("i", "p"), engine.methodFamily(hierarchy, "i", "run", "()V"));
    }

    @Test
    void privateAndStaticMethodsAreRenamedAlone() {
        ClassNode base = classNode("p", "java/lang/Object", Opcodes.ACC_PUBLIC);
        base.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "x", "()V", null, null));
        ClassNode child = classNode("q", "p", Opcodes.ACC_PUBLIC);
        child.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "x", "()V", null, null));

        ClassHierarchy hierarchy = new ClassHierarchy(List.of(base, child));

        assertEquals(Set.of("p"), engine.methodFamily(hierarchy, "p", "x", "()V"));
        assertEquals(Set.of("q"), engine.methodFamily(hierarchy, "q", "x", "()V"));
    }

    /**
     * a { static int f; int m() { return 1; } }
     * ab extends a { int m() { return 2; } }
     * c { static int run() { a x = new ab(); ab.f = 10; ... return x.m() + ab.f; }  // 12
     *     static void take(a, ab, String, a[]) {} }
     */
    static List<ClassNode> program() {
        List<byte[]> classes = new ArrayList<>();

        ClassWriter a = writer();
        a.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "a", null, "java/lang/Object", null);
        a.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "f", "I", null, null).visitEnd();
        constructor(a, "java/lang/Object");
        returnsInt(a, 1);
        a.visitEnd();
        classes.add(a.toByteArray());

        ClassWriter ab = writer();
        ab.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "ab", null, "a", null);
        constructor(ab, "a");
        returnsInt(ab, 2);
        ab.visitEnd();
        classes.add(ab.toByteArray());

        ClassWriter c = writer();
        c.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "c", null, "java/lang/Object", null);
        MethodVisitor take = c.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "take", "(La;Lab;Ljava/lang/String;[La;)V", null, null
        );
        take.visitCode();
        take.visitInsn(Opcodes.RETURN);
        take.visitMaxs(0, 0);
        take.visitEnd();

        MethodVisitor run = c.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        run.visitCode();
        run.visitTypeInsn(Opcodes.NEW, "ab");
        run.visitInsn(Opcodes.DUP);
        run.visitMethodInsn(Opcodes.INVOKESPECIAL, "ab", "<init>", "()V", false);
        run.visitTypeInsn(Opcodes.CHECKCAST, "a");
        run.visitVarInsn(Opcodes.ASTORE, 0);
        // Field declared in a, written through subclass ab.
        run.visitIntInsn(Opcodes.BIPUSH, 10);
        run.visitFieldInsn(Opcodes.PUTSTATIC, "ab", "f", "I");
        // A branch forces a stack map frame whose locals mention a.
        Label otherwise = new Label();
        Label end = new Label();
        run.visitVarInsn(Opcodes.ALOAD, 0);
        run.visitTypeInsn(Opcodes.INSTANCEOF, "a");
        run.visitJumpInsn(Opcodes.IFEQ, otherwise);
        run.visitVarInsn(Opcodes.ALOAD, 0);
        run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a", "m", "()I", false);
        run.visitVarInsn(Opcodes.ISTORE, 1);
        run.visitJumpInsn(Opcodes.GOTO, end);
        run.visitLabel(otherwise);
        run.visitInsn(Opcodes.ICONST_0);
        run.visitVarInsn(Opcodes.ISTORE, 1);
        run.visitLabel(end);
        run.visitInsn(Opcodes.ICONST_1);
        run.visitTypeInsn(Opcodes.ANEWARRAY, "a");
        run.visitTypeInsn(Opcodes.CHECKCAST, "[La;");
        run.visitInsn(Opcodes.POP);
        run.visitLdcInsn(Type.getObjectType("a"));
        run.visitInsn(Opcodes.POP);
        run.visitVarInsn(Opcodes.ILOAD, 1);
        run.visitFieldInsn(Opcodes.GETSTATIC, "ab", "f", "I");
        run.visitInsn(Opcodes.IADD);
        run.visitInsn(Opcodes.IRETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();
        c.visitEnd();
        classes.add(c.toByteArray());

        List<ClassNode> nodes = new ArrayList<>();
        for (byte[] bytes : classes) {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Writes the classes without recomputing frames, verifies them, then loads them and calls c.run().
     */
    static int run(List<ClassNode> classes) throws Exception {
        Map<String, byte[]> bytesByName = new HashMap<>();
        for (ClassNode classNode : classes) {
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            bytesByName.put(classNode.name.replace('/', '.'), writer.toByteArray());
        }
        ClassLoader loader = new ClassLoader(RenameEngineTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = bytesByName.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        for (byte[] bytes : bytesByName.values()) {
            StringWriter errors = new StringWriter();
            CheckClassAdapter.verify(new ClassReader(bytes), loader, false, new PrintWriter(errors));
            assertTrue(errors.toString().isEmpty(), errors.toString());
        }
        return (int) loader.loadClass("c").getMethod("run").invoke(null);
    }

    private static ClassWriter writer() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String left, String right) {
                return "java/lang/Object";
            }
        };
    }

    private static void constructor(ClassWriter writer, String superName) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void returnsInt(ClassWriter writer, int value) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m", "()I", null, null);
        method.visitCode();
        method.visitIntInsn(Opcodes.BIPUSH, value);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    static ClassNode find(List<ClassNode> classes, String name) {
        return classes.stream().filter(classNode -> classNode.name.equals(name)).findFirst().orElseThrow();
    }

    static ClassNode classNode(String name, String superName, int access) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = access;
        classNode.name = name;
        classNode.superName = superName;
        return classNode;
    }
}
