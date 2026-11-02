package org.de.rikr.ui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigationHistoryTest {
    private final NavigationHistory history = new NavigationHistory();
    private final ClassNode first = classNode("a");
    private final ClassNode second = classNode("b");
    private final ClassNode third = classNode("c");

    @Test
    void goesBackAndForward() {
        history.visited(first);
        history.visited(second);
        history.visited(third);

        assertEquals(Optional.of(second), history.back(classNode -> true));
        assertEquals(Optional.of(first), history.back(classNode -> true));
        assertEquals(Optional.empty(), history.back(classNode -> true));
        assertEquals(Optional.of(second), history.forward(classNode -> true));
    }

    @Test
    void visitingAfterGoingBackDropsTheForwardHistory() {
        history.visited(first);
        history.visited(second);
        history.back(classNode -> true);
        history.visited(third);

        assertEquals(Optional.empty(), history.forward(classNode -> true));
        assertEquals(Optional.of(first), history.back(classNode -> true));
    }

    @Test
    void skipsClassesNoLongerLoadedAndRepeatedVisits() {
        history.visited(first);
        history.visited(second);
        history.visited(second);
        history.visited(third);

        assertEquals(Optional.of(first), history.back(classNode -> classNode != second));
    }

    @Test
    void listsRecentClassesNewestFirstOnce() {
        history.visited(first);
        history.visited(second);
        history.visited(first);

        assertEquals(List.of(first, second), history.recent(5));
        assertEquals(List.of(first), history.recent(1));
        history.clear();
        assertEquals(List.of(), history.recent(5));
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        return classNode;
    }
}
