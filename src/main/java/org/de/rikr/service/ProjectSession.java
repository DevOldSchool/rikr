package org.de.rikr.service;

import java.util.List;
import java.util.Objects;

public record ProjectSession(
    List<String> jarPaths,
    List<ClassMappingDecision> mappings,
    List<RenameOperation> renames,
    List<ProjectBookmark> bookmarks,
    ProjectLayout layout
) {
    public ProjectSession {
        jarPaths = List.copyOf(jarPaths);
        mappings = List.copyOf(mappings);
        renames = List.copyOf(renames);
        bookmarks = List.copyOf(bookmarks);
        Objects.requireNonNull(layout, "layout");
    }
}
