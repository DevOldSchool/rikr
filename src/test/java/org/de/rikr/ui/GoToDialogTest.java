package org.de.rikr.ui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoToDialogTest {
    @Test
    void ranksExactThenPrefixThenContainsWithClassesFirst() {
        ClassNode client = classNode("org/example/Client");
        ClassNode clientLoader = classNode("org/example/ClientLoader");
        ClassNode myClient = classNode("MyClient");
        MethodNode clientMethod = new MethodNode(Opcodes.ACC_PUBLIC, "client", "()V", null, null);
        client.methods.add(clientMethod);

        GoToDialog.Target exactClass = new GoToDialog.Target("a.jar", client, null, null);
        GoToDialog.Target exactMethod = new GoToDialog.Target("a.jar", client, null, clientMethod);
        GoToDialog.Target prefix = new GoToDialog.Target("a.jar", clientLoader, null, null);
        GoToDialog.Target contains = new GoToDialog.Target("a.jar", myClient, null, null);

        List<GoToDialog.Target> ranked = List.of(contains, prefix, exactMethod, exactClass).stream()
            .sorted(Comparator.comparingInt(target -> GoToDialog.rank(target, "client")))
            .toList();

        assertEquals(List.of(exactClass, exactMethod, prefix, contains), ranked);
    }

    @Test
    void describesMembersWithReadableTypes() {
        ClassNode owner = classNode("a/B");
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "run", "(ILjava/lang/String;)Z", null, null);

        GoToDialog.Target target = new GoToDialog.Target("a.jar", owner, null, method);

        assertEquals("run", target.name());
        assertEquals("(int, String) boolean   in a/B", target.detail());
        assertEquals("B", new GoToDialog.Target("a.jar", owner, null, null).name());
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        return classNode;
    }
}
