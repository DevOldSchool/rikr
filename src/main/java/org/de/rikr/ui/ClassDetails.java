package org.de.rikr.ui;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plain-text summary of a class for comparing mapping candidates side by side, with Java types in place of
 * descriptors.
 */
final class ClassDetails {
    private static final String FIELDS = "Fields (";
    private static final String METHODS = "Methods (";

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
        details.append("Superclass: ").append(classNode.superName == null ? "<none>" : classNode.superName)
            .append('\n');
        details.append("Interfaces (").append(classNode.interfaces.size()).append("):\n");
        classNode.interfaces.forEach(name -> details.append("  ").append(name).append('\n'));
        details.append('\n').append(FIELDS).append(classNode.fields.size()).append("):\n");
        for (FieldNode field : classNode.fields) {
            details.append("  ");
            if (Modifier.isStatic(field.access)) {
                details.append("static ");
            }
            details.append(ClassNodeUtil.simpleName(Type.getType(field.desc))).append(' ').append(field.name).append('\n');
        }
        details.append('\n').append(METHODS).append(classNode.methods.size()).append("):\n");
        for (MethodNode method : classNode.methods) {
            details.append("  ");
            if (Modifier.isStatic(method.access)) {
                details.append("static ");
            }
            Type type = Type.getMethodType(method.desc);
            List<String> arguments = new ArrayList<>();
            for (Type argument : type.getArgumentTypes()) {
                arguments.add(ClassNodeUtil.simpleName(argument));
            }
            details.append(ClassNodeUtil.simpleName(type.getReturnType())).append(' ').append(method.name)
                .append('(').append(String.join(", ", arguments)).append(")\n");
        }
        return details.toString();
    }

    /**
     * The field and method lines of {@code details}, the description of {@code classNode}, with no member of the same
     * shape in {@code other}, as line numbers from 0. Shapes leave out names and the JAR's own class names, since
     * both change between obfuscated versions; JDK types and primitives are compared.
     */
    static List<Integer> unmatchedMembers(String details, ClassNode classNode, ClassNode other) {
        Map<String, Integer> available = new HashMap<>();
        shapes(other).forEach(shape -> available.merge(shape, 1, Integer::sum));
        List<String> shapes = shapes(classNode);
        List<Integer> lines = memberLines(details);
        List<Integer> unmatched = new ArrayList<>();
        for (int index = 0; index < shapes.size() && index < lines.size(); index++) {
            Integer count = available.get(shapes.get(index));
            if (count == null || count == 0) {
                unmatched.add(lines.get(index));
            } else {
                available.put(shapes.get(index), count - 1);
            }
        }
        return unmatched;
    }

    private static List<String> shapes(ClassNode classNode) {
        List<String> shapes = new ArrayList<>();
        for (FieldNode field : classNode.fields) {
            shapes.add("F" + (field.access & Modifier.STATIC) + shape(Type.getType(field.desc)));
        }
        for (MethodNode method : classNode.methods) {
            Type type = Type.getMethodType(method.desc);
            StringBuilder shape = new StringBuilder("M" + (method.access & Modifier.STATIC) + "(");
            for (Type argument : type.getArgumentTypes()) {
                shape.append(shape(argument)).append(',');
            }
            shapes.add(shape.append(')').append(shape(type.getReturnType())).toString());
        }
        return shapes;
    }

    private static String shape(Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        String dimensions = type.getSort() == Type.ARRAY ? "[]".repeat(type.getDimensions()) : "";
        if (element.getSort() == Type.OBJECT && !element.getInternalName().startsWith("java/")) {
            return "object" + dimensions;
        }
        return element.getClassName() + dimensions;
    }

    // The member lines in the order describe writes them: fields, then methods.
    private static List<Integer> memberLines(String details) {
        String[] lines = details.split("\n", -1);
        List<Integer> members = new ArrayList<>();
        boolean inMembers = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (!line.startsWith("  ")) {
                inMembers = line.startsWith(FIELDS) || line.startsWith(METHODS);
            } else if (inMembers) {
                members.add(index);
            }
        }
        return members;
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
