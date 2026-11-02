package org.de.rikr.rename;

import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

/**
 * What an undo reverted, or a redo applied again.
 *
 * @param affectedClass the class to show afterwards, or null for a whole batch
 */
public record RenameUndoResult(RenameStep step, RenameResult result, ClassNode affectedClass) {
    public RenameUndoResult {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(result, "result");
    }
}
