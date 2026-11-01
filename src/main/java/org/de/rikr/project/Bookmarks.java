package org.de.rikr.project;

import org.de.rikr.bookmark.BookmarkService;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.model.ClassRef;
import org.de.rikr.model.Workspace;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Optional;

/**
 * The project's bookmarks. Renames and JAR removal update them automatically.
 */
public class Bookmarks {
    private final Workspace workspace;
    private final EventBus events;
    private final BookmarkService service = new BookmarkService();

    Bookmarks(Workspace workspace, EventBus events) {
        this.workspace = workspace;
        this.events = events;
    }

    /**
     * @return true if the bookmark was added, false if it was removed
     */
    public boolean toggle(ProjectBookmark bookmark) {
        boolean added = service.toggle(bookmark);
        events.publish(new ProjectEvent.BookmarksChanged());
        return added;
    }

    public void remove(ProjectBookmark bookmark) {
        service.remove(bookmark);
        events.publish(new ProjectEvent.BookmarksChanged());
    }

    public List<ProjectBookmark> all() {
        return service.getBookmarks();
    }

    public boolean contains(ProjectBookmark bookmark) {
        return service.getBookmarks().contains(bookmark);
    }

    public Optional<ClassNode> findClass(ProjectBookmark bookmark) {
        return workspace.findClass(new ClassRef(bookmark.jarName(), bookmark.className()));
    }

    void replaceAll(List<ProjectBookmark> bookmarks) {
        service.replaceAll(bookmarks);
    }

    BookmarkService service() {
        return service;
    }
}
