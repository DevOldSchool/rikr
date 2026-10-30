package org.de.rikr.project;

import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectSessionServiceTest {
    @Test
    void roundTripsCompleteProjectState(@TempDir Path directory) throws Exception {
        ProjectLayout layout = new ProjectLayout(10, 20, 1200, 800, 300, 600, 700, true, false, true, "BOOKMARKS");
        ProjectSession session = new ProjectSession(
            List.of("/tmp/234.jar", "/tmp/235.jar"),
            List.of(new ClassMappingDecision("234.jar", "235.jar", "a", "b", 97)),
            List.of(new RenameOperation(RenameType.CLASS, "234.jar", "a", null, "a", "Calendar")),
            List.of(new ProjectBookmark(ProjectBookmarkType.METHOD, "234.jar", "Calendar", "find", "()V")),
            layout
        );
        Path destination = directory.resolve("analysis.rikr");
        ProjectSessionService service = new ProjectSessionService();

        service.save(destination.toFile(), session);

        assertEquals(session, service.load(destination.toFile()));
    }

    @Test
    void rejectsUnsupportedVersions(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("future.rikr");
        Files.writeString(source, "format.version=999\n");

        assertThrows(
            java.io.IOException.class,
            () -> new ProjectSessionService().load(source.toFile())
        );
    }
}
