package org.de.rikr.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BookmarkService {
    private final Set<ProjectBookmark> bookmarks = new LinkedHashSet<>();

    public synchronized boolean toggle(ProjectBookmark bookmark) {
        Objects.requireNonNull(bookmark, "bookmark");
        if (bookmarks.remove(bookmark)) {
            return false;
        }
        bookmarks.add(bookmark);
        return true;
    }

    public synchronized void remove(ProjectBookmark bookmark) {
        bookmarks.remove(bookmark);
    }

    public synchronized void removeJar(String jarName) {
        bookmarks.removeIf(bookmark -> bookmark.jarName().equals(jarName));
    }

    public synchronized List<ProjectBookmark> getBookmarks() {
        return List.copyOf(bookmarks);
    }

    public synchronized void replaceAll(List<ProjectBookmark> restoredBookmarks) {
        bookmarks.clear();
        bookmarks.addAll(restoredBookmarks);
    }

    public synchronized void applyRename(RenameOperation operation, boolean reverse) {
        String from = reverse ? operation.newName() : operation.oldName();
        String to = reverse ? operation.oldName() : operation.newName();
        Set<ProjectBookmark> updated = new LinkedHashSet<>();
        for (ProjectBookmark bookmark : bookmarks) {
            if (!bookmark.jarName().equals(operation.jarName())) {
                updated.add(bookmark);
            } else if (operation.type() == RenameType.CLASS && bookmark.className().equals(from)) {
                updated.add(new ProjectBookmark(
                    bookmark.type(), bookmark.jarName(), to, bookmark.memberName(), bookmark.descriptor()
                ));
            } else if (operation.type() != RenameType.CLASS
                && bookmark.className().equals(operation.ownerName())
                && Objects.equals(bookmark.memberName(), from)
                && (operation.type() == RenameType.METHOD
                    || Objects.equals(bookmark.descriptor(), operation.descriptor()))) {
                updated.add(new ProjectBookmark(
                    bookmark.type(), bookmark.jarName(), bookmark.className(), to, bookmark.descriptor()
                ));
            } else {
                updated.add(bookmark);
            }
        }
        bookmarks.clear();
        bookmarks.addAll(updated);
    }

    public synchronized void clear() {
        bookmarks.clear();
    }
}
