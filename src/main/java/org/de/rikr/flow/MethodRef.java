package org.de.rikr.flow;

import java.util.Objects;

/**
 * A method named by its owner's internal name, its name and its descriptor. It may be outside the JAR.
 */
public record MethodRef(String owner, String name, String descriptor) {
    public MethodRef {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public String toString() {
        return owner + "." + name + descriptor;
    }
}
