package org.de.rikr.ui;

import org.de.rikr.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchPanelFilterTest {
    private final ClassNode first = classNode("a/First");
    private final ClassNode second = classNode("a/Second");
    private final MethodNode method = new MethodNode(0, "run", "()V", null, null);
    private final FieldNode field = new FieldNode(0, "count", "I", null, null);

    private final SearchResult firstClass = SearchResult.classResult(first, first.name, "class", true);
    private final SearchResult firstMethod = SearchResult.methodResult(first, method, "run", "run", "run()V");
    private final SearchResult firstCall = SearchResult.instructionResult(first, "INVOKEVIRTUAL B.run", "run", "x");
    private final SearchResult secondClass = SearchResult.classResult(second, second.name, "class", true);
    private final SearchResult secondField = SearchResult.fieldResult(second, field, "count", "count", "count");
    private final List<SearchResult> results = List.of(firstClass, firstMethod, firstCall, secondClass, secondField);

    @Test
    void allKeepsEveryRow() {
        assertEquals(results, SearchPanel.filter(results, SearchPanel.Kind.ALL));
    }

    @Test
    void instructionsKeepTheirClassAndMethod() {
        assertEquals(List.of(firstClass, firstMethod, firstCall),
            SearchPanel.filter(results, SearchPanel.Kind.INSTRUCTIONS));
    }

    @Test
    void membersDropClassesWithoutMatchingMembers() {
        assertEquals(List.of(firstClass, firstMethod, secondClass, secondField),
            SearchPanel.filter(results, SearchPanel.Kind.MEMBERS));
    }

    @Test
    void classesListEachClass() {
        assertEquals(List.of(firstClass, secondClass), SearchPanel.filter(results, SearchPanel.Kind.CLASSES));
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        return classNode;
    }
}
