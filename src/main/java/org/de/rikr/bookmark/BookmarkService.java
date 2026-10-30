package org.de.rikr.bookmark;

import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameType;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

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

    /**
     * @param memberOwners for member renames, every class whose declaration was renamed (a method rename also
     *                     renames overriding declarations in other classes)
     */
    public synchronized void applyRename(RenameOperation operation, boolean reverse, Set<String> memberOwners) {
        String from = reverse ? operation.newName() : operation.oldName();
        String to = reverse ? operation.oldName() : operation.newName();
        Set<ProjectBookmark> updated = new LinkedHashSet<>();
        for (ProjectBookmark bookmark : bookmarks) {
            if (!bookmark.jarName().equals(operation.jarName())) {
                updated.add(bookmark);
            } else if (operation.type() == RenameType.CLASS) {
                Remapper remapper = new SimpleRemapper(from, to);
                updated.add(new ProjectBookmark(
                    bookmark.type(),
                    bookmark.jarName(),
                    remapper.mapType(bookmark.className()),
                    bookmark.memberName(),
                    remapDescriptor(remapper, bookmark)
                ));
            } else if (memberOwners.contains(bookmark.className())
                && Objects.equals(bookmark.memberName(), from)
                && Objects.equals(bookmark.descriptor(), operation.descriptor())) {
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

    private String remapDescriptor(Remapper remapper, ProjectBookmark bookmark) {
        if (bookmark.descriptor() == null) {
            return null;
        }
        return bookmark.type() == ProjectBookmarkType.METHOD
            ? remapper.mapMethodDesc(bookmark.descriptor())
            : remapper.mapDesc(bookmark.descriptor());
    }

    public synchronized void clear() {
        bookmarks.clear();
    }
}
