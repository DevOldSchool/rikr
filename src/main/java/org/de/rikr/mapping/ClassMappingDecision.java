package org.de.rikr.mapping;

import java.util.Objects;

public record ClassMappingDecision(
    String sourceJar,
    String targetJar,
    String sourceClassName,
    String targetClassName,
    int similarityPercent
) {
    public ClassMappingDecision {
        Objects.requireNonNull(sourceJar, "sourceJar");
        Objects.requireNonNull(targetJar, "targetJar");
        Objects.requireNonNull(sourceClassName, "sourceClassName");
        Objects.requireNonNull(targetClassName, "targetClassName");
        if (similarityPercent < 0 || similarityPercent > 100) {
            throw new IllegalArgumentException("similarityPercent must be between 0 and 100");
        }
    }
}
