package org.de.rikr.utilities;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;

public class ClassNodeUtil {
    public static String getPattern(ClassNode classNode, FieldNode fieldNode, MethodNode methodNode) {
        if (classNode != null && !Modifier.isInterface(classNode.access)) {
            return getClassPattern(classNode);
        } else if (classNode != null && Modifier.isInterface(classNode.access)) {
            return getInterfacePattern(classNode);
        } else if (fieldNode != null) {
            return getFieldNodePattern(fieldNode);
        } else if (methodNode != null) {
            return getMethodNodePattern(methodNode);
        }

        return "";
    }

    public static String getClassPattern(ClassNode classNode) {
        return "class " + classNode.name;
    }

    public static String getInterfacePattern(ClassNode classNode) {
        return "interface " + classNode.name;
    }

    public static String getFieldNodePattern(FieldNode fieldNode) {
        return fieldNode.desc + " " + fieldNode.name;
    }

    public static String getMethodNodePattern(MethodNode methodNode) {
        return methodNode.desc + methodNode.name;
    }
}
