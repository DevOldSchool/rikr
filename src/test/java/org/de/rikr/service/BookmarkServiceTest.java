package org.de.rikr.service;

import org.junit.jupiter.api.Test;

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
            false
        );

        ProjectBookmark renamed = service.getBookmarks().get(0);
        assertEquals("renamed", renamed.memberName());
        assertFalse(service.toggle(renamed));
        assertTrue(service.getBookmarks().isEmpty());
    }
}
