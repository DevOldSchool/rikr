package org.de.rikr.model;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The names classes, fields and methods had when they were loaded. Renames change nodes in place and keep their
 * identity, so the nodes themselves are the keys.
 */
public final class OriginalNames {

    /**
     * A field or method as it was loaded: its name and its descriptor in the loaded class names.
     */
    public record Member(String name, String descriptor) {
    }

    private final Map<ClassNode, String> classes = new IdentityHashMap<>();
    private final Map<FieldNode, Member> fields = new IdentityHashMap<>();
    private final Map<MethodNode, Member> methods = new IdentityHashMap<>();

    synchronized void record(ClassNode classNode) {
        classes.put(classNode, classNode.name);
        for (FieldNode field : classNode.fields) {
            fields.put(field, new Member(field.name, field.desc));
        }
        for (MethodNode method : classNode.methods) {
            methods.put(method, new Member(method.name, method.desc));
        }
    }

    synchronized void forget(ClassNode classNode) {
        classes.remove(classNode);
        classNode.fields.forEach(fields::remove);
        classNode.methods.forEach(methods::remove);
    }

    /**
     * The class's name when it was loaded, or its current name if it was not recorded.
     */
    public synchronized String className(ClassNode classNode) {
        return classes.getOrDefault(classNode, classNode.name);
    }

    public synchronized Member field(FieldNode field) {
        return fields.getOrDefault(field, new Member(field.name, field.desc));
    }

    public synchronized Member method(MethodNode method) {
        return methods.getOrDefault(method, new Member(method.name, method.desc));
    }
}
