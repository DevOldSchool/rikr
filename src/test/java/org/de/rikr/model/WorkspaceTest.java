package org.de.rikr.model;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTest {
    private final Workspace workspace = new Workspace();

    @Test
    void findsClassesByNameAfterTheyAreRenamed() {
        ClassNode classNode = classNode("a");
        workspace.addJar("gamepack.jar", List.of(classNode), new File("gamepack.jar"));
        assertEquals(Optional.of(classNode), workspace.findClass(new ClassRef("gamepack.jar", "a")));

        classNode.name = "Player";
        workspace.classesChanged("gamepack.jar");

        assertEquals(Optional.of(classNode), workspace.findClass(new ClassRef("gamepack.jar", "Player")));
        assertTrue(workspace.findClass(new ClassRef("gamepack.jar", "a")).isEmpty());
    }

    @Test
    void refusesASecondJarWithTheSameName() {
        workspace.addJar("gamepack.jar", List.of(), new File("one/gamepack.jar"));

        assertThrows(
            IllegalArgumentException.class,
            () -> workspace.addJar("gamepack.jar", List.of(), new File("two/gamepack.jar"))
        );
    }

    @Test
    void looseClassesHaveNoSourceFileAndAreNotReopenable() {
        ClassNode loose = classNode("Loose");
        workspace.addJar("gamepack.jar", List.of(), new File("gamepack.jar"));
        workspace.addLooseClass(loose);

        assertNull(workspace.sourceFile(Workspace.LOOSE_CLASSES));
        assertEquals(List.of("gamepack.jar"), List.copyOf(workspace.sourceFiles().keySet()));
        assertEquals(Optional.of(loose), workspace.findClass(new ClassRef(Workspace.LOOSE_CLASSES, "Loose")));
    }

    @Test
    void removedClassesAreNoLongerFound() {
        ClassNode classNode = classNode("a");
        workspace.addJar("gamepack.jar", List.of(classNode), new File("gamepack.jar"));
        workspace.findClass(new ClassRef("gamepack.jar", "a"));

        assertTrue(workspace.removeClass("gamepack.jar", classNode));

        assertTrue(workspace.findClass(new ClassRef("gamepack.jar", "a")).isEmpty());
        assertTrue(workspace.classes("gamepack.jar").isEmpty());
    }

    @Test
    void classListsAreReadOnly() {
        workspace.addJar("gamepack.jar", List.of(classNode("a")), new File("gamepack.jar"));

        assertThrows(UnsupportedOperationException.class, () -> workspace.classes("gamepack.jar").clear());
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        return classNode;
    }
}
