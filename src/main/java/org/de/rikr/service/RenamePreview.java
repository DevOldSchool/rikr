package org.de.rikr.service;

import java.util.Objects;
import java.util.List;

public record RenamePreview(
    RenameType type,
    String oldName,
    String newName,
    boolean valid,
    String validationMessage,
    int referenceCount,
    int fileCount,
    List<String> affectedClasses
) {
    public RenamePreview {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(oldName, "oldName");
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(validationMessage, "validationMessage");
        affectedClasses = List.copyOf(affectedClasses);
    }

    public static RenamePreview invalid(RenameType type, String oldName, String newName, String message) {
        return new RenamePreview(type, oldName, newName, false, message, 0, 0, List.of());
    }

    public static RenamePreview valid(
        RenameType type,
        String oldName,
        String newName,
        RenameResult result,
        List<String> affectedClasses
    ) {
        return new RenamePreview(
            type,
            oldName,
            newName,
            true,
            "",
            result.referenceCount(),
            result.fileCount(),
            affectedClasses
        );
    }
}
