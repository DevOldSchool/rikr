package org.de.rikr.bookmark;

import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public record ProjectBookmark(
    ProjectBookmarkType type,
    String jarName,
    String className,
    String memberName,
    String descriptor
) {
    public ProjectBookmark {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(jarName, "jarName");
        Objects.requireNonNull(className, "className");
    }

    /**
     * The bookmarked class or member as people write it, such as {@code a.B.run(int, String) : void}: dotted names,
     * with descriptors as Java types and {@code java.lang.} left off.
     */
    public String label() {
        String owner = className.replace('/', '.');
        if (memberName == null) {
            return owner;
        }
        if (descriptor == null) {
            return owner + "." + memberName;
        }
        if (type == ProjectBookmarkType.METHOD) {
            Type method = Type.getMethodType(descriptor);
            String parameters = Arrays.stream(method.getArgumentTypes()).map(ProjectBookmark::typeName)
                .collect(Collectors.joining(", "));
            return owner + "." + memberName + "(" + parameters + ") : " + typeName(method.getReturnType());
        }
        return owner + "." + memberName + " : " + typeName(Type.getType(descriptor));
    }

    private static String typeName(Type type) {
        String name = type.getClassName();
        return name.startsWith("java.lang.") && name.indexOf('.', "java.lang.".length()) < 0
            ? name.substring("java.lang.".length()) : name;
    }

    @Override
    public String toString() {
        String target = className;
        if (memberName != null) {
            target += "." + memberName + (descriptor == null ? "" : descriptor);
        }
        return jarName + " -> " + target;
    }
}
