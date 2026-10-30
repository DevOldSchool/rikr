package org.de.rikr.rename;

import java.util.Objects;

public record RenameOperation(
    RenameType type,
    String jarName,
    String ownerName,
    String descriptor,
    String oldName,
    String newName
) {
    public RenameOperation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(jarName, "jarName");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(oldName, "oldName");
        Objects.requireNonNull(newName, "newName");
    }
}
