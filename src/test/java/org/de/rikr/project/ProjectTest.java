package org.de.rikr.project;

import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.jar.JarExportService;
import org.de.rikr.jar.LoadedJar;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.ClassMatch;
import org.de.rikr.mapping.SimilarClassReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTest {
    @TempDir
    Path tempDir;

    private final List<String> log = new ArrayList<>();
    private final Project project = new Project(log::add);

    @AfterEach
    void close() {
        project.close();
    }

    @Test
    void refusesSecondJarWithTheSameFileName() throws Exception {
        File first = writeJar("one", "gamepack.jar", classNode("first/Only"));
        File second = writeJar("two", "gamepack.jar", classNode("second/Only"));

        List<String> loaded = project.jars().loadJars(new File[]{first, second});

        assertEquals(List.of("gamepack.jar"), loaded);
        assertEquals("first/Only", project.jars().classes("gamepack.jar").getFirst().name);
        assertEquals(first.getAbsoluteFile(), project.jars().sourceFile("gamepack.jar"));
        assertTrue(log.stream().anyMatch(message -> message.contains("already loaded")));
    }

    @Test
    void skipsReadJarWhoseNameWasLoadedBeforeItWasAdded() throws Exception {
        File first = writeJar("one", "gamepack.jar", classNode("first/Only"));
        File second = writeJar("two", "gamepack.jar", classNode("second/Only"));
        List<LoadedJar> read = project.jars().readJars(List.of(second));
        List<ProjectEvent> seen = new ArrayList<>();
        project.events().subscribe(seen::add);

        project.jars().loadJars(new File[]{first});
        List<String> added = project.jars().addJars(read);

        assertEquals(List.of(), added);
        assertEquals("first/Only", project.jars().classes("gamepack.jar").getFirst().name);
        assertEquals(1, seen.size());
        assertTrue(log.stream().anyMatch(message -> message.contains("already loaded")));
    }

    @Test
    void classRenameUpdatesMappingsBookmarksAndReferencesBeforeOtherListenersRun() throws Exception {
        loadOldAndNew();
        ClassNode source = project.jars().classes("old.jar").getFirst();
        ClassNode target = project.jars().classes("new.jar").getFirst();
        project.mappings().accept("old.jar", "new.jar", source, new ClassMatch(target, 100));
        project.bookmarks().toggle(new ProjectBookmark(ProjectBookmarkType.CLASS, "old.jar", "a", null, null));
        List<ProjectEvent> seen = new ArrayList<>();
        project.events().subscribe(event -> {
            seen.add(event);
            String expected = ((ProjectEvent.Renamed) event).undo() ? "a" : "Player";
            assertEquals(expected, project.mappings().allAccepted().getFirst().sourceClassName());
        });

        project.renames().renameClass("old.jar", source, "Player");

        assertInstanceOf(ProjectEvent.Renamed.class, seen.getFirst());
        assertEquals(
            List.of(new ClassMappingDecision("old.jar", "new.jar", "Player", "a", 100)),
            project.mappings().allAccepted()
        );
        assertEquals("Player", project.bookmarks().all().getFirst().className());
        assertEquals(source, project.bookmarks().findClass(project.bookmarks().all().getFirst()).orElseThrow());
        assertEquals(1, project.references().findTypeReferences("old.jar", source).size());
        assertEquals(Set.of("old.jar"), project.renames().dirtyJars());

        project.renames().undoLast();

        assertEquals("a", project.mappings().allAccepted().getFirst().sourceClassName());
        assertEquals("a", project.bookmarks().all().getFirst().className());
    }

    @Test
    void fieldRenameKeepsMappings() throws Exception {
        loadOldAndNew();
        ClassNode source = project.jars().classes("old.jar").getFirst();
        ClassNode target = project.jars().classes("new.jar").getFirst();
        project.mappings().accept("old.jar", "new.jar", source, new ClassMatch(target, 100));

        project.renames().renameField("old.jar", source, source.fields.getFirst(), "self");

        assertEquals(1, project.mappings().allAccepted().size());
    }

    @Test
    void removingAJarDropsItsRenamesMappingsAndBookmarks() throws Exception {
        loadOldAndNew();
        ClassNode source = project.jars().classes("old.jar").getFirst();
        ClassNode target = project.jars().classes("new.jar").getFirst();
        project.mappings().accept("old.jar", "new.jar", source, new ClassMatch(target, 100));
        project.bookmarks().toggle(new ProjectBookmark(ProjectBookmarkType.CLASS, "old.jar", "a", null, null));
        project.renames().renameClass("old.jar", source, "Player");

        project.jars().removeJar("old.jar");

        assertTrue(project.mappings().allAccepted().isEmpty());
        assertTrue(project.bookmarks().all().isEmpty());
        assertTrue(project.renames().history().isEmpty());
        assertTrue(project.renames().dirtyJars().isEmpty());
    }

    private void loadOldAndNew() throws Exception {
        project.jars().loadJars(new File[]{
            writeJar("old", "old.jar", classWithSelfField("a")),
            writeJar("new", "new.jar", classWithSelfField("a"))
        });
    }

    @Test
    void similarClassReportListsMatchesInOtherJarsWithEquivalentMethods() throws Exception {
        project.jars().loadJars(new File[]{
            writeJar("old", "old.jar", classReturning("a", "m", 7)),
            writeJar("new", "new.jar", classReturning("b", "n", 7))
        });
        ClassNode source = project.jars().classes("old.jar").getFirst();

        SimilarClassReport report = project.mappings().similarClassReport("old.jar", source);

        assertEquals(1, report.matches().size());
        SimilarClassReport.Match match = report.matches().getFirst();
        assertEquals("new.jar", match.jar());
        assertEquals("b", match.match().classNode().name);
        assertEquals(1, match.equivalentMethods().size());
        assertEquals("m", match.equivalentMethods().getFirst().source().name);
        assertEquals("n", match.equivalentMethods().getFirst().target().name);
    }

    private static ClassNode classReturning(String name, String method, int value) {
        ClassNode classNode = classNode(name);
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "f", "I", null, null));
        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, "()I", null, null);
        methodNode.instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
        methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));
        methodNode.maxStack = 1;
        classNode.methods.add(methodNode);
        return classNode;
    }

    private File writeJar(String directory, String name, ClassNode classNode) throws Exception {
        File jar = tempDir.resolve(directory).resolve(name).toFile();
        new JarExportService(message -> {
        }).exportJar(jar, List.of(classNode));
        return jar;
    }

    private static ClassNode classWithSelfField(String name) {
        ClassNode classNode = classNode(name);
        classNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "f", "L" + name + ";", null, null));
        return classNode;
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        return classNode;
    }
}
