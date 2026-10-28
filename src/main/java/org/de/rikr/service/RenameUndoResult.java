package org.de.rikr.service;

import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

public record RenameUndoResult(RenameOperation operation, RenameResult result, ClassNode affectedClass) {
    public RenameUndoResult {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(affectedClass, "affectedClass");
    }
}
