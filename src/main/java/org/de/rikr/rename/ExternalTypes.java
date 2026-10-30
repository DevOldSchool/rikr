package org.de.rikr.rename;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Looks up JDK types that a JAR extends but does not contain, such as {@code java/lang/Object} or
 * {@code java/awt/Canvas}. Types that are neither in the JAR nor in the JDK are treated as unknown.
 */
final class ExternalTypes {
    private ExternalTypes() {
    }

    /**
     * Whether the external type, or one of its supertypes, declares a method that subclasses override when they
     * declare {@code name + descriptor}.
     */
    static boolean declaresOverridableMethod(String internalName, String name, String descriptor) {
        Class<?> start = load(internalName);
        if (start == null) {
            return false;
        }
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            Class<?> current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            try {
                for (Method method : current.getDeclaredMethods()) {
                    int modifiers = method.getModifiers();
                    if (method.getName().equals(name)
                        && !Modifier.isPrivate(modifiers)
                        && !Modifier.isStatic(modifiers)
                        && Type.getMethodDescriptor(method).equals(descriptor)) {
                        return true;
                    }
                }
            } catch (LinkageError | SecurityException ignored) {
                // Treat a type whose members cannot be inspected as declaring nothing.
            }
            if (current.getSuperclass() != null) {
                pending.add(current.getSuperclass());
            }
            for (Class<?> interfaceType : current.getInterfaces()) {
                pending.add(interfaceType);
            }
        }
        return false;
    }

    private static Class<?> load(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }
}
