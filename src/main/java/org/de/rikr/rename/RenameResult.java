package org.de.rikr.rename;

import java.util.Set;

/**
 * @param declarationOwners classes whose declaration was renamed: the class itself for a class rename, the
 *                          owner for a field rename, and every class in the override family for a method rename
 */
public record RenameResult(String type, int referenceCount, int fileCount, Set<String> declarationOwners) {
    public RenameResult {
        declarationOwners = Set.copyOf(declarationOwners);
    }

    public RenameResult(String type, int referenceCount, int fileCount) {
        this(type, referenceCount, fileCount, Set.of());
    }

    public RenameResult withType(String newType) {
        return new RenameResult(newType, referenceCount, fileCount, declarationOwners);
    }
}
