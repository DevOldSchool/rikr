package org.de.rikr.flow;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryPointsAndCallGraphTest {

    @Test
    void theManifestsMainClassComesFirstThenOtherMains() {
        ClassNode tool = classNode("tools/Tool", "java/lang/Object", 0);
        tool.methods.add(method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V"));
        ClassNode app = classNode("app/Main", "java/lang/Object", 0);
        app.methods.add(method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V"));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "app.Main");

        List<EntryPoint> entries = EntryPoints.find(List.of(tool, app), manifest);

        assertEquals(List.of(EntryPoint.Kind.MAIN_CLASS, EntryPoint.Kind.MAIN),
            entries.stream().map(EntryPoint::kind).toList());
        assertEquals(new MethodRef("app/Main", "main", "([Ljava/lang/String;)V"), entries.getFirst().method());
    }

    /**
     * An obfuscated game client: {@code client} extends an abstract shell that is a Runnable with start, stop and
     * destroy, but neither extends {@code Applet}.
     */
    @Test
    void anObfuscatedAppletIsFoundByItsLifecycleAndItsOwnRunComesFirst() {
        ClassNode shell = classNode("df", "java/lang/Object", Opcodes.ACC_ABSTRACT);
        shell.interfaces.add("java/lang/Runnable");
        shell.methods.add(method(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "init", "()V"));
        for (String name : List.of("start", "stop", "destroy", "run")) {
            shell.methods.add(method(Opcodes.ACC_PUBLIC, name, "()V"));
        }
        ClassNode client = classNode("client", "df", 0);
        client.methods.add(method(Opcodes.ACC_PUBLIC, "init", "()V"));
        ClassNode worker = classNode("aa", "java/lang/Thread", 0);
        worker.methods.add(method(Opcodes.ACC_PUBLIC, "run", "()V"));
        ClassNode keys = classNode("kb", "java/lang/Object", 0);
        keys.interfaces.add("java/awt/event/KeyListener");
        keys.methods.add(method(Opcodes.ACC_PUBLIC, "keyPressed", "(Ljava/awt/event/KeyEvent;)V"));
        keys.methods.add(method(Opcodes.ACC_PUBLIC, "a", "()V"));

        List<String> entries = EntryPoints.find(List.of(worker, shell, client, keys), null).stream()
            .map(entry -> entry.kind() + " " + entry.method())
            .toList();

        assertEquals(List.of(
            "APPLET client.init()V",
            "APPLET df.start()V",
            "THREAD df.run()V",
            "THREAD aa.run()V",
            "LISTENER kb.keyPressed(Ljava/awt/event/KeyEvent;)V"
        ), entries);
    }

    @Test
    void aRealAppletSubclassIsAnEntryPoint() {
        ClassNode applet = classNode("game/Loader", "java/applet/Applet", 0);
        applet.methods.add(method(Opcodes.ACC_PUBLIC, "init", "()V"));

        List<EntryPoint> entries = EntryPoints.find(List.of(applet), null);

        assertEquals(new MethodRef("game/Loader", "init", "()V"), entries.getFirst().method());
        assertEquals(EntryPoint.Kind.APPLET, entries.getFirst().kind());
    }

    @Test
    void callsResolveToDeclarationsAndListOverridesTheyMayDispatchTo() {
        ClassNode base = classNode("a", "java/lang/Object", 0);
        base.methods.add(method(Opcodes.ACC_PUBLIC, "m", "()V"));
        ClassNode child = classNode("b", "a", 0);
        child.methods.add(method(Opcodes.ACC_PUBLIC, "m", "()V"));
        child.methods.add(method(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "lambda$0", "()V"));
        ClassNode caller = classNode("c", "java/lang/Object", 0);
        MethodNode run = method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(La;)V");
        run.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a", "m", "()V", false));
        run.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "c", "helper", "()V", false));
        run.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false));
        Handle metafactory = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                + "Ljava/lang/invoke/CallSite;", false);
        Handle body = new Handle(Opcodes.H_INVOKESTATIC, "b", "lambda$0", "()V", false);
        run.instructions.add(new InvokeDynamicInsnNode("run", "()Ljava/lang/Runnable;", metafactory,
            Type.getType("()V"), body, Type.getType("()V")));
        run.instructions.add(new InsnNode(Opcodes.RETURN));
        caller.methods.add(run);
        MethodNode helper = method(Opcodes.ACC_STATIC, "helper", "()V");
        helper.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "b", "m", "()V", false));
        caller.methods.add(helper);
        CallGraph graph = new CallGraph(List.of(base, child, caller));
        MethodRef root = new MethodRef("c", "run", "(La;)V");

        List<String> calls = graph.callees(root).stream()
            .map(call -> call.kind() + " " + call.target() + (call.inJar() ? "" : " outside"))
            .toList();

        assertEquals(List.of(
            "VIRTUAL a.m()V",
            "OVERRIDE b.m()V",
            "STATIC c.helper()V",
            "VIRTUAL java/lang/Object.hashCode()I outside",
            "LAMBDA b.lambda$0()V"
        ), calls);
        assertEquals(List.of(root), List.copyOf(graph.reachable(root, 1, 10, false).keySet()));
        var twoDeep = graph.reachable(root, 2, 10, false);
        assertTrue(twoDeep.containsKey(new MethodRef("c", "helper", "()V")));
        assertFalse(twoDeep.get(root).stream().anyMatch(call -> !call.inJar()), "library calls are left out");
    }

    private static ClassNode classNode(String name, String superName, int access) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC | access;
        classNode.name = name;
        classNode.superName = superName;
        return classNode;
    }

    private static MethodNode method(int access, String name, String descriptor) {
        return new MethodNode(access, name, descriptor, null, null);
    }
}
