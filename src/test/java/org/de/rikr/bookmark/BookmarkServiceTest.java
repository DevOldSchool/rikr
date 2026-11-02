package org.de.rikr.bookmark;

import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkServiceTest {
    @Test
    void togglesAndFollowsRenames() {
        BookmarkService service = new BookmarkService();
        ProjectBookmark bookmark = new ProjectBookmark(
            ProjectBookmarkType.FIELD,
            "app.jar",
            "Owner",
            "field",
            "I"
        );

        assertTrue(service.toggle(bookmark));
        service.applyRename(
            new RenameOperation(RenameType.FIELD, "app.jar", "Owner", "I", "field", "renamed"),
            false,
            Set.of("Owner")
        );

        ProjectBookmark renamed = service.getBookmarks().get(0);
        assertEquals("renamed", renamed.memberName());
        assertFalse(service.toggle(renamed));
        assertTrue(service.getBookmarks().isEmpty());
    }

    @Test
    void followsMethodOverridesAndClassRenamesInDescriptors() {
        BookmarkService service = new BookmarkService();
        service.toggle(new ProjectBookmark(ProjectBookmarkType.METHOD, "app.jar", "Child", "m", "(La;)V"));
        service.toggle(new ProjectBookmark(ProjectBookmarkType.METHOD, "app.jar", "Child", "m", "(I)V"));

        service.applyRename(
            new RenameOperation(RenameType.METHOD, "app.jar", "Base", "(La;)V", "m", "renamed"),
            false,
            Set.of("Base", "Child")
        );
        service.applyRename(
            new RenameOperation(RenameType.CLASS, "app.jar", "a", null, "a", "Foo"),
            false,
            Set.of("a")
        );

        assertEquals(
            List.of(
                new ProjectBookmark(ProjectBookmarkType.METHOD, "app.jar", "Child", "renamed", "(LFoo;)V"),
                new ProjectBookmark(ProjectBookmarkType.METHOD, "app.jar", "Child", "m", "(I)V")
            ),
            service.getBookmarks()
        );
    }

    @Test
    void labelsMembersWithJavaTypes() {
        assertEquals("a.B", new ProjectBookmark(ProjectBookmarkType.CLASS, "app.jar", "a/B", null, null).label());
        assertEquals("a.B.count : int",
            new ProjectBookmark(ProjectBookmarkType.FIELD, "app.jar", "a/B", "count", "I").label());
        assertEquals("a.B.run(int, String[], java.util.List) : a.C",
            new ProjectBookmark(ProjectBookmarkType.METHOD, "app.jar", "a/B", "run",
                "(I[Ljava/lang/String;Ljava/util/List;)La/C;").label());
    }
}
