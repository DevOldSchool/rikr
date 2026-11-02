package org.de.rikr.reference;

import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

public record ReferenceResult(
    ReferenceKind kind,
    String text,
    ClassNode navigationClass,
    String navigationPattern,
    String highlightText
) {
    public ReferenceResult {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    public boolean isNavigable() {
        return navigationClass != null && navigationPattern != null && highlightText != null;
    }
}
