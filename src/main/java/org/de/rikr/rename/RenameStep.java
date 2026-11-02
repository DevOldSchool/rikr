package org.de.rikr.rename;

/**
 * One entry of the rename history: a single rename, or a batch of renames imported from a mapping file. Undo
 * reverts a whole step.
 */
public sealed interface RenameStep permits RenameOperation, RenameBatch {

    String jarName();
}
