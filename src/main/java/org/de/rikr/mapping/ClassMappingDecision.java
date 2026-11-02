package org.de.rikr.mapping;

import java.util.Objects;

/**
 * A class of one JAR accepted as the match of a class of another.
 *
 * @param automatic whether Auto-match accepted it rather than a person; Auto-match may replace its own matches
 */
public record ClassMappingDecision(
    String sourceJar,
    String targetJar,
    String sourceClassName,
    String targetClassName,
    int similarityPercent,
    boolean automatic
) {
    /**
     * A match a person accepted.
     */
    public ClassMappingDecision(String sourceJar, String targetJar, String sourceClassName, String targetClassName,
                                int similarityPercent) {
        this(sourceJar, targetJar, sourceClassName, targetClassName, similarityPercent, false);
    }

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
