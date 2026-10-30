package org.de.rikr.project;

import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSessionServiceTest {
    @TempDir
    Path directory;

    private final ProjectSessionService service = new ProjectSessionService();

    @Test
    void roundTripsCompleteProjectStateAsJson() throws Exception {
        ProjectSession session = session();
        Path destination = directory.resolve("analysis.rikr");

        service.save(destination.toFile(), session);

        String json = Files.readString(destination);
        assertTrue(json.startsWith("{\n  \"formatVersion\": 2,"), json);
        assertTrue(json.contains("\"newName\": \"Calendar\""), json);
        assertEquals(session, service.load(destination.toFile()));
    }

    @Test
    void opensVersionOnePropertiesFiles() throws Exception {
        Path source = directory.resolve("old.rikr");
        Files.writeString(source, """
            format.version=1
            jar.count=2
            jar.0=/tmp/234.jar
            jar.1=/tmp/235.jar
            mapping.count=1
            mapping.0.sourceJar=234.jar
            mapping.0.targetJar=235.jar
            mapping.0.sourceClass=a
            mapping.0.targetClass=b
            mapping.0.similarity=97
            rename.count=1
            rename.0.type=CLASS
            rename.0.jar=234.jar
            rename.0.owner=a
            rename.0.descriptor=
            rename.0.oldName=a
            rename.0.newName=Calendar
            bookmark.count=1
            bookmark.0.type=METHOD
            bookmark.0.jar=234.jar
            bookmark.0.class=Calendar
            bookmark.0.member=find
            bookmark.0.descriptor=()V
            layout.x=10
            layout.y=20
            layout.width=1200
            layout.height=800
            layout.mainDivider=300
            layout.verticalDivider=600
            layout.contentDivider=700
            layout.logVisible=true
            layout.simulatorVisible=false
            layout.splitJavaVisible=true
            layout.workspace=BOOKMARKS
            """);

        assertEquals(session(), service.load(source.toFile()));
    }

    @Test
    void rejectsFilesFromANewerVersion() throws Exception {
        Path source = directory.resolve("future.rikr");
        Files.writeString(source, "{\"formatVersion\": 3}");

        IOException error = assertThrows(IOException.class, () -> service.load(source.toFile()));

        assertTrue(error.getMessage().contains("newer version of Rikr"), error.getMessage());
    }

    @Test
    void namesTheMissingFieldOfAnIncompleteFile() throws Exception {
        Path source = directory.resolve("broken.rikr");
        Files.writeString(source, """
            {"formatVersion": 2, "jars": [], "renames": [{"type": "CLASS", "jar": "a.jar", "owner": "a"}],
             "mappings": [], "bookmarks": [], "layout": {"workspace": "PROJECT"}}
            """);

        IOException error = assertThrows(IOException.class, () -> service.load(source.toFile()));

        assertEquals("Project file is missing renames[0].oldName", error.getMessage());
    }

    @Test
    void rejectsUnsupportedLegacyVersions() throws Exception {
        Path source = directory.resolve("odd.rikr");
        Files.writeString(source, "format.version=999\n");

        assertThrows(IOException.class, () -> service.load(source.toFile()));
    }

    private static ProjectSession session() {
        return new ProjectSession(
            List.of("/tmp/234.jar", "/tmp/235.jar"),
            List.of(new ClassMappingDecision("234.jar", "235.jar", "a", "b", 97)),
            List.of(new RenameOperation(RenameType.CLASS, "234.jar", "a", null, "a", "Calendar")),
            List.of(new ProjectBookmark(ProjectBookmarkType.METHOD, "234.jar", "Calendar", "find", "()V")),
            new ProjectLayout(10, 20, 1200, 800, 300, 600, 700, true, false, true, "BOOKMARKS")
        );
    }
}
