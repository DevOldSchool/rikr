package org.de.rikr.rename;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up JDK types that a JAR extends but does not contain, such as {@code java/lang/Object} or
 * {@code java/awt/Canvas}. Types that are neither in the JAR nor in the JDK are treated as unknown.
 */
final class ExternalTypes {
    private ExternalTypes() {
    }

    // JDK types do not change while Rikr runs, so each type's overridable methods are collected once.
    private static final Map<String, Set<String>> OVERRIDABLE = new ConcurrentHashMap<>();

    /**
     * Whether the external type, or one of its supertypes, declares a method that subclasses override when they
     * declare {@code name + descriptor}.
     */
    static boolean declaresOverridableMethod(String internalName, String name, String descriptor) {
        return OVERRIDABLE.computeIfAbsent(internalName, ExternalTypes::overridableMethods).contains(name + descriptor);
    }

    private static Set<String> overridableMethods(String internalName) {
        Class<?> start = load(internalName);
        if (start == null) {
            return Set.of();
        }
        Set<String> methods = new HashSet<>();
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
                    if (!Modifier.isPrivate(modifiers) && !Modifier.isStatic(modifiers)) {
                        methods.add(method.getName() + Type.getMethodDescriptor(method));
                    }
                }
            } catch (LinkageError | SecurityException ignored) {
                // Treat a type whose members cannot be inspected as declaring nothing.
            }
            if (current.getSuperclass() != null) {
                pending.add(current.getSuperclass());
            }
            pending.addAll(List.of(current.getInterfaces()));
        }
        return Set.copyOf(methods);
    }

    private static Class<?> load(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }
}
