package org.de.rikr.ui;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;

/**
 * A plain-text summary of a class for comparing mapping candidates side by side.
 */
final class ClassDetails {
    private ClassDetails() {
    }

    /**
     * @param similarityPercent shown first when not null
     */
    static String describe(ClassNode classNode, Integer similarityPercent) {
        if (classNode == null) {
            return "";
        }
        StringBuilder details = new StringBuilder();
        if (similarityPercent != null) {
            details.append("Similarity: ").append(similarityPercent).append("%\n");
        }
        details.append("Name: ").append(classNode.name).append('\n');
        details.append("Kind: ").append(kind(classNode)).append('\n');
        details.append("Superclass: ").append(classNode.superName == null ? "<none>" : classNode.superName).append('\n');
        details.append("Interfaces (").append(classNode.interfaces.size()).append("):\n");
        classNode.interfaces.forEach(name -> details.append("  ").append(name).append('\n'));
        details.append("\nFields (").append(classNode.fields.size()).append("):\n");
        for (FieldNode field : classNode.fields) {
            details.append("  ");
            if (Modifier.isStatic(field.access)) {
                details.append("static ");
            }
            details.append(field.name).append(' ').append(field.desc).append('\n');
        }
        details.append("\nMethods (").append(classNode.methods.size()).append("):\n");
        for (MethodNode method : classNode.methods) {
            details.append("  ");
            if (Modifier.isStatic(method.access)) {
                details.append("static ");
            }
            details.append(method.name).append(method.desc).append('\n');
        }
        return details.toString();
    }

    private static String kind(ClassNode classNode) {
        if (Modifier.isInterface(classNode.access)) {
            return "interface";
        }
        if (Modifier.isAbstract(classNode.access)) {
            return "abstract class";
        }
        return "class";
    }
}
