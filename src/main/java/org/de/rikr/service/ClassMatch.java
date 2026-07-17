package org.de.rikr.service;

import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

public record ClassMatch(ClassNode classNode, int similarityPercent) {
    public ClassMatch {
        Objects.requireNonNull(classNode, "classNode");
        if (similarityPercent < 0 || similarityPercent > 100) {
            throw new IllegalArgumentException("similarityPercent must be between 0 and 100");
        }
    }
}
