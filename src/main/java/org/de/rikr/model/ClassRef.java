package org.de.rikr.model;

import java.util.Objects;

/**
 * A class identified by the JAR it was loaded from and its current internal name.
 */
public record ClassRef(String jar, String className) {
    public ClassRef {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(className, "className");
    }

    public ClassRef withClassName(String newClassName) {
        return new ClassRef(jar, newClassName);
    }

    @Override
    public String toString() {
        return jar + " -> " + className;
    }
}
