package org.de.rikr.bookmark;

import java.util.Objects;

public record ProjectBookmark(
    ProjectBookmarkType type,
    String jarName,
    String className,
    String memberName,
    String descriptor
) {
    public ProjectBookmark {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(jarName, "jarName");
        Objects.requireNonNull(className, "className");
    }

    @Override
    public String toString() {
        String target = className;
        if (memberName != null) {
            target += "." + memberName + (descriptor == null ? "" : descriptor);
        }
        return jarName + " -> " + target;
    }
}
