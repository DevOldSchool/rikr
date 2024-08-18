package org.de.rikr.utilities;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        return methodNode.name + methodNode.desc;
    }

    public static ClassNode matchClassNode(List<ClassNode> classes, String className, String methodName, String methodDesc) {
        Set<String> visitedClasses = new HashSet<>();
        return findMethodOwnerRecursively(classes, className, methodName, methodDesc, visitedClasses);
    }

    public static ClassNode findMethodOwnerRecursively(List<ClassNode> classes, String className, String methodName, String methodDesc, Set<String> visitedClasses) {
        if (visitedClasses.contains(className)) {
            return null;
        }
        visitedClasses.add(className);

        ClassNode classNode = findClassNode(classes, className);
        if (classNode == null) {
            return null;
        }

        // Check if the classNode contains the method
        List<MethodNode> methods = classNode.methods;
        for (MethodNode method : methods) {
            if (method.name.equals(methodName) && method.desc.equals(methodDesc)) {
                return classNode;
            }
        }

        // Check superclasses and interfaces
        if (classNode.superName != null) {
            ClassNode owner = findMethodOwnerRecursively(classes, classNode.superName, methodName, methodDesc, visitedClasses);
            if (owner != null) {
                return owner;
            }
        }

        for (String interfaceName : classNode.interfaces) {
            ClassNode owner = findMethodOwnerRecursively(classes, interfaceName, methodName, methodDesc, visitedClasses);
            if (owner != null) {
                return owner;
            }
        }

        return null;
    }

    public static ClassNode findClassNode(List<ClassNode> classes, String className) {
        for (ClassNode classNode : classes) {
            if (classNode.name.equals(className)) {
                return classNode;
            }
        }

        try {
            InputStream classStream = ClassNodeUtil.class.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class");
            if (classStream == null) {
                return null;
            }

            ClassReader classReader = new ClassReader(classStream);
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, 0);

            return classNode;
        } catch (IOException ignored) {
        }

        return null;
    }

    public static void stripAnnotations(ClassNode classNode) {
        if (classNode.visibleAnnotations != null) {
            classNode.visibleAnnotations.clear();
        }
        if (classNode.invisibleAnnotations != null) {
            classNode.invisibleAnnotations.clear();
        }

        for (FieldNode fieldNode : classNode.fields) {
            if (fieldNode.visibleAnnotations != null) {
                fieldNode.visibleAnnotations.clear();
            }
            if (fieldNode.invisibleAnnotations != null) {
                fieldNode.invisibleAnnotations.clear();
            }
        }

        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.visibleAnnotations != null) {
                methodNode.visibleAnnotations.clear();
            }
            if (methodNode.invisibleAnnotations != null) {
                methodNode.invisibleAnnotations.clear();
            }
        }
    }
}
