package org.de.rikr.mapping;

import java.util.Objects;

/**
 * A field or method of one JAR matched to a field or method of another, usually the next version of the program.
 *
 * @param confidencePercent how alike the two are, from 0 to 100
 */
public record MemberMatch(MemberRef source, MemberRef target, int confidencePercent, Reason reason) {
    public MemberMatch {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (confidencePercent < 0 || confidencePercent > 100) {
            throw new IllegalArgumentException("confidencePercent must be between 0 and 100");
        }
    }

    /**
     * A field or method by the internal name of its declaring class, its name and its descriptor.
     */
    public record MemberRef(String owner, String name, String descriptor) {
        public MemberRef {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
        }

        @Override
        public String toString() {
            return owner + "." + name + (descriptor.startsWith("(") ? "" : ":") + descriptor;
        }
    }

    /**
     * Why two members were matched, strongest first.
     */
    public enum Reason {
        /** Same code, and the same values at every step when simulated. */
        SAME_BEHAVIOUR("behaves the same"),
        /** Similar instructions, strings, calls and field accesses. */
        SIMILAR_CODE("similar code"),
        /** Read and written by methods that were matched to each other. */
        SIMILAR_USE("used by the same methods"),
        /** The only member of its kind and type on both sides, with nothing that says otherwise. */
        ONLY_CANDIDATE("the only one of its type");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}
