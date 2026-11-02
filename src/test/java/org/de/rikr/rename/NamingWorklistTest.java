package org.de.rikr.rename;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamingWorklistTest {
    private final Map<ClassNode, String> loadedAs = new HashMap<>();
    private final Map<ClassNode, Integer> references = new HashMap<>();

    @Test
    void countsClassesInNoPackageAndListsTheMostReferencedUnnamed() {
        ClassNode ab = loaded("ab", 3);
        ClassNode cd = loaded("cd", 9);
        ClassNode ef = loaded("ef", 9);
        ClassNode client = loaded("client", 50);
        loaded("org/lib/Choice", 100);

        NamingWorklist.Progress progress = NamingWorklist.of(List.copyOf(loadedAs.keySet()), loadedAs::get,
            references::get, 10);

        assertEquals(4, progress.classes());
        assertEquals(1, progress.named());
        assertEquals(List.of(cd, ef, ab), progress.next().stream().map(NamingWorklist.Entry::classNode).toList());
        assertEquals(9, progress.next().getFirst().references());
        assertEquals("client", client.name);
    }

    @Test
    void aRenamedClassCountsAsNamedUnlessItsNameIsMadeUp() {
        ClassNode ab = loaded("ab", 1);
        ClassNode cd = loaded("cd", 1);
        loaded("ef", 1);
        ab.name = "Player";
        cd.name = "class12";

        NamingWorklist.Progress progress = NamingWorklist.of(List.copyOf(loadedAs.keySet()), loadedAs::get,
            references::get, 10);

        assertEquals(3, progress.classes());
        assertEquals(1, progress.named());
        assertEquals(2, progress.next().size());
    }

    @Test
    void countsEveryClassWhenAllAreInPackages() {
        loaded("app/ab", 1);
        loaded("app/cd", 2);
        loaded("app/Player", 3);

        NamingWorklist.Progress progress = NamingWorklist.of(List.copyOf(loadedAs.keySet()), loadedAs::get,
            references::get, 1);

        assertEquals(3, progress.classes());
        assertEquals(1, progress.named());
        assertEquals("app/cd", progress.next().getFirst().classNode().name);
        assertEquals(1, progress.next().size());
    }

    @Test
    void aJarNamedByPeopleHasNothingToName() {
        loaded("Player", 1);
        loaded("Npc", 1);

        NamingWorklist.Progress progress = NamingWorklist.of(List.copyOf(loadedAs.keySet()), loadedAs::get,
            references::get, 10);

        assertEquals(2, progress.named());
        assertEquals(List.of(), progress.next());
    }

    private ClassNode loaded(String name, int referenceCount) {
        ClassNode classNode = new ClassNode();
        classNode.name = name;
        loadedAs.put(classNode, name);
        references.put(classNode, referenceCount);
        return classNode;
    }
}
