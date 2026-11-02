package org.de.rikr.rename;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of the type hierarchy of one JAR. Names outside the JAR (for example {@code java/lang/Object})
 * show up as ancestors but have no {@link ClassNode}.
 */
public final class ClassHierarchy {
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Map<String, Set<String>> ancestors = new HashMap<>();

    public ClassHierarchy(List<ClassNode> classNodes) {
        for (ClassNode classNode : classNodes) {
            classes.put(classNode.name, classNode);
        }
        for (ClassNode classNode : classNodes) {
            for (String parent : parents(classNode)) {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(classNode.name);
            }
        }
    }

    public ClassNode get(String name) {
        return classes.get(name);
    }

    public boolean contains(String name) {
        return classes.containsKey(name);
    }

    public Collection<ClassNode> classes() {
        return classes.values();
    }

    /**
     * The class itself followed by every supertype, including supertypes that are not part of the JAR.
     */
    public Set<String> selfAndAncestors(String name) {
        Set<String> cached = ancestors.get(name);
        if (cached != null) {
            return cached;
        }
        Set<String> result = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(name);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            ClassNode classNode = classes.get(current);
            if (classNode != null) {
                pending.addAll(parents(classNode));
            }
        }
        ancestors.put(name, result);
        return result;
    }

    /**
     * The class itself followed by every class in the JAR that extends or implements it.
     */
    public Set<String> selfAndDescendants(String name) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(name);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (result.add(current)) {
                pending.addAll(children.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    /**
     * JVM method resolution (JVMS 5.4.3.3) restricted to classes in the JAR.
     *
     * @return the name of the class whose declaration the reference resolves to, or null if it resolves
     * outside the JAR
     */
    public String resolveMethod(String owner, String name, String descriptor) {
        String current = owner;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            ClassNode classNode = classes.get(current);
            if (classNode == null) {
                break;
            }
            if (findMethod(classNode, name, descriptor) != null) {
                return current;
            }
            current = classNode.superName;
        }
        for (String candidate : selfAndAncestors(owner)) {
            ClassNode classNode = classes.get(candidate);
            if (classNode != null && isInterface(classNode) && findMethod(classNode, name, descriptor) != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * JVM field resolution (JVMS 5.4.3.2) restricted to classes in the JAR.
     */
    public String resolveField(String owner, String name, String descriptor) {
        return resolveField(owner, name, descriptor, new LinkedHashSet<>());
    }

    private String resolveField(String owner, String name, String descriptor, Set<String> visited) {
        ClassNode classNode = classes.get(owner);
        if (classNode == null || !visited.add(owner)) {
            return null;
        }
        if (findField(classNode, name, descriptor) != null) {
            return owner;
        }
        for (String interfaceName : classNode.interfaces) {
            String resolved = resolveField(interfaceName, name, descriptor, visited);
            if (resolved != null) {
                return resolved;
            }
        }
        return classNode.superName == null ? null : resolveField(classNode.superName, name, descriptor, visited);
    }

    public static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        return null;
    }

    public static FieldNode findField(ClassNode classNode, String name, String descriptor) {
        for (FieldNode field : classNode.fields) {
            if (field.name.equals(name) && field.desc.equals(descriptor)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Whether the method takes part in virtual dispatch, so that overrides must be renamed together.
     */
    public static boolean isOverridable(MethodNode method) {
        return (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0 && !method.name.startsWith("<");
    }

    private static boolean isInterface(ClassNode classNode) {
        return (classNode.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private static List<String> parents(ClassNode classNode) {
        List<String> parents = new ArrayList<>();
        if (classNode.superName != null) {
            parents.add(classNode.superName);
        }
        parents.addAll(classNode.interfaces);
        return parents;
    }
}
