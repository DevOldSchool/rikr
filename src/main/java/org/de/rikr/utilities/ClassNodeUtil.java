package org.de.rikr.utilities;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassNodeUtil {
    /**
     * A type's name without its package, such as {@code int}, {@code String} or {@code byte[]}.
     */
    public static String simpleName(Type type) {
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

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
        // Class-level annotations
        if (classNode.visibleAnnotations != null) {
            classNode.visibleAnnotations.clear();
        }
        if (classNode.invisibleAnnotations != null) {
            classNode.invisibleAnnotations.clear();
        }
        if (classNode.visibleTypeAnnotations != null) {
            classNode.visibleTypeAnnotations.clear();
        }
        if (classNode.invisibleTypeAnnotations != null) {
            classNode.invisibleTypeAnnotations.clear();
        }
        // Record components (Java 16+)
        if (classNode.recordComponents != null) {
            for (org.objectweb.asm.tree.RecordComponentNode rc : classNode.recordComponents) {
                if (rc.visibleAnnotations != null) rc.visibleAnnotations.clear();
                if (rc.invisibleAnnotations != null) rc.invisibleAnnotations.clear();
                if (rc.visibleTypeAnnotations != null) rc.visibleTypeAnnotations.clear();
                if (rc.invisibleTypeAnnotations != null) rc.invisibleTypeAnnotations.clear();
            }
        }

        // Field-level annotations
        for (FieldNode fieldNode : classNode.fields) {
            if (fieldNode.visibleAnnotations != null) {
                fieldNode.visibleAnnotations.clear();
            }
            if (fieldNode.invisibleAnnotations != null) {
                fieldNode.invisibleAnnotations.clear();
            }
            if (fieldNode.visibleTypeAnnotations != null) {
                fieldNode.visibleTypeAnnotations.clear();
            }
            if (fieldNode.invisibleTypeAnnotations != null) {
                fieldNode.invisibleTypeAnnotations.clear();
            }
        }

        // Method-level annotations
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.visibleAnnotations != null) {
                methodNode.visibleAnnotations.clear();
            }
            if (methodNode.invisibleAnnotations != null) {
                methodNode.invisibleAnnotations.clear();
            }
            if (methodNode.visibleTypeAnnotations != null) {
                methodNode.visibleTypeAnnotations.clear();
            }
            if (methodNode.invisibleTypeAnnotations != null) {
                methodNode.invisibleTypeAnnotations.clear();
            }
            // Parameter annotations
            if (methodNode.visibleParameterAnnotations != null) {
                for (var arr : methodNode.visibleParameterAnnotations) {
                    if (arr != null) arr.clear();
                }
            }
            if (methodNode.invisibleParameterAnnotations != null) {
                for (var arr : methodNode.invisibleParameterAnnotations) {
                    if (arr != null) arr.clear();
                }
            }
            // Local variable annotations (if present)
            if (methodNode.visibleLocalVariableAnnotations != null) {
                methodNode.visibleLocalVariableAnnotations.clear();
            }
            if (methodNode.invisibleLocalVariableAnnotations != null) {
                methodNode.invisibleLocalVariableAnnotations.clear();
            }
            // Default value of annotation methods
            methodNode.annotationDefault = null;
        }
    }

    public static MethodNode getMethodNode(ClassNode classNode, MethodInsnNode methodInsnNode) {
        for (MethodNode methodNode : classNode.methods) {
            if (methodInsnNode.name.equals(methodNode.name) && methodInsnNode.desc.equals(methodNode.desc)) {
                return methodNode;
            }
        }

        return null;
    }

    public static List<ClassNode> findClassesExtending(List<ClassNode> classes, ClassNode baseClassNode) {
        List<ClassNode> extendedClasses = new ArrayList<>();
        for (ClassNode classNode : classes) {
            if (baseClassNode.name.equals(classNode.superName)) {
                extendedClasses.add(classNode);
            }
        }
        return extendedClasses;
    }

    public static List<ClassNode> findClassesImplementing(List<ClassNode> classes, ClassNode baseClassNode) {
        List<ClassNode> implementingClasses = new ArrayList<>();
        for (ClassNode classNode : classes) {
            if (classNode.interfaces.contains(baseClassNode.name)) {
                implementingClasses.add(classNode);
            }
        }
        return implementingClasses;
    }

    public static Map<String, List<ClassNode>> groupBySuperclass(List<ClassNode> classes) {
        Map<String, List<ClassNode>> superclassGroups = new HashMap<>();
        for (ClassNode classNode : classes) {
            if (classNode.superName != null) {
                superclassGroups.computeIfAbsent(classNode.superName, k -> new ArrayList<>()).add(classNode);
            }
        }
        return superclassGroups;
    }

    public static Map<String, List<ClassNode>> groupByInterface(List<ClassNode> classes) {
        Map<String, List<ClassNode>> interfaceGroups = new HashMap<>();
        for (ClassNode classNode : classes) {
            if (classNode.interfaces != null) {
                for (String interfaceName : classNode.interfaces) {
                    interfaceGroups.computeIfAbsent(interfaceName, k -> new ArrayList<>()).add(classNode);
                }
            }
        }
        return interfaceGroups;
    }
}
