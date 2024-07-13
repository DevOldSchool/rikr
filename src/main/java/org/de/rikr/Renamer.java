package org.de.rikr;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class Renamer {
    private static int renamedClassReferenceCounter;
    private static int renamedClassReferenceFileCounter;
    private static int renamedMethodReferenceCounter;
    private static int renamedMethodReferenceFileCounter;
    private static int renamedFieldReferenceCounter;
    private static int renamedFieldReferenceFileCounter;

    /**
     * Update references to the specified class.
     *
     * @param classes List of class nodes to iterate over
     * @param oldName Old name to find in class nodes
     * @param newName New name to replace old name with in class nodes
     */
    public static void updateClassReferences(List<ClassNode> classes, String oldName, String newName) {
        renamedClassReferenceCounter = 0;
        renamedClassReferenceFileCounter = 0;

        for (ClassNode classNode : classes) {
            int referenceCounter = renamedClassReferenceCounter;

            // Update class name references
            if (classNode.name.equals(oldName)) {
                classNode.name = newName;
                renamedClassReferenceCounter++;
            }

            // Update extended class name reference
            if (classNode.superName.equals(oldName)) {
                classNode.superName = newName;
                renamedClassReferenceCounter++;
            }

            // Update field references
            for (FieldNode fieldNode : classNode.fields) {
                if (fieldNode.desc.contains(String.format("L%s;", oldName))) {
                    fieldNode.desc = fieldNode.desc.replace(oldName, newName);
                    renamedClassReferenceCounter++;
                }

                // Update field annotation references
                if (fieldNode.visibleAnnotations != null) {
                    for (AnnotationNode annotationNode : fieldNode.visibleAnnotations) {
                        if (annotationNode.desc.contains(String.format("L%s;", oldName))) {
                            annotationNode.desc = annotationNode.desc.replace(String.format("L%s;", oldName), String.format("L%s;", newName));
                            renamedClassReferenceCounter++;
                        }
                    }
                }

                if (fieldNode.invisibleAnnotations != null) {
                    for (AnnotationNode annotationNode : fieldNode.invisibleAnnotations) {
                        if (annotationNode.desc.contains(String.format("L%s;", oldName))) {
                            annotationNode.desc = annotationNode.desc.replace(String.format("L%s;", oldName), String.format("L%s;", newName));
                            renamedClassReferenceCounter++;
                        }
                    }
                }
            }

            // Update interface references
            List<String> interfacesToRemove = new ArrayList<>();
            List<String> interfacesToAdd = new ArrayList<>();
            for (String interfaceName : classNode.interfaces) {
                if (interfaceName.equals(oldName)) {
                    interfacesToRemove.add(interfaceName);
                    interfacesToAdd.add(newName);
                    renamedClassReferenceCounter++;
                }
            }
            classNode.interfaces.removeAll(interfacesToRemove);
            classNode.interfaces.addAll(interfacesToAdd);

            // Update inner class references
            for (InnerClassNode innerClassNode : classNode.innerClasses) {
                if (innerClassNode.name.equals(oldName)) {
                    innerClassNode.name = newName;
                    renamedClassReferenceCounter++;
                }
            }

            // Update method references
            for (MethodNode methodNode : classNode.methods) {
                // Update parameters and return types
                if (methodNode.desc.contains(String.format("L%s;", oldName))) {
                    methodNode.desc = methodNode.desc.replace(oldName, newName);
                    renamedClassReferenceCounter++;
                }

                // Update instructions
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    // Field references
                    if (insnNode instanceof FieldInsnNode fieldInsnNode) {
                        if (fieldInsnNode.owner.equals(oldName)) {
                            fieldInsnNode.owner = newName;
                            renamedClassReferenceCounter++;
                        }

                        if (fieldInsnNode.desc.contains(String.format("L%s;", oldName))) {
                            fieldInsnNode.desc = fieldInsnNode.desc.replace(oldName, newName);
                            renamedClassReferenceCounter++;
                        }
                    }

                    // Method references
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        if (methodInsnNode.owner.equals(oldName)) {
                            methodInsnNode.owner = newName;
                            renamedClassReferenceCounter++;
                        }

                        if (methodInsnNode.desc.contains(String.format("L%s;", oldName))) {
                            methodInsnNode.desc = methodInsnNode.desc.replace(oldName, newName);
                            renamedClassReferenceCounter++;
                        }
                    }

                    // Type references
                    if (insnNode instanceof TypeInsnNode typeInsnNode) {
                        if (typeInsnNode.desc.equals(oldName)) {
                            typeInsnNode.desc = newName;
                            renamedClassReferenceCounter++;
                        }
                    }
                }

                // Update try-catch blocks
                for (TryCatchBlockNode tryCatchBlock : methodNode.tryCatchBlocks) {
                    if (tryCatchBlock.type != null && tryCatchBlock.type.equals(oldName)) {
                        tryCatchBlock.type = newName;
                        renamedClassReferenceCounter++;
                    }
                }

                // Update method annotation references
                if (methodNode.visibleAnnotations != null) {
                    for (AnnotationNode annotationNode : methodNode.visibleAnnotations) {
                        if (annotationNode.desc.contains(String.format("L%s;", oldName))) {
                            annotationNode.desc = annotationNode.desc.replace(String.format("L%s;", oldName), String.format("L%s;", newName));
                            renamedClassReferenceCounter++;
                        }
                    }
                }

                if (methodNode.invisibleAnnotations != null) {
                    for (AnnotationNode annotationNode : methodNode.invisibleAnnotations) {
                        if (annotationNode.desc.contains(String.format("L%s;", oldName))) {
                            annotationNode.desc = annotationNode.desc.replace(String.format("L%s;", oldName), String.format("L%s;", newName));
                            renamedClassReferenceCounter++;
                        }
                    }
                }
            }

            if (referenceCounter != renamedClassReferenceCounter) {
                renamedClassReferenceFileCounter++;
            }
        }
    }

    /**
     * Update references to the specified method.
     *
     * @param classes List of class nodes to iterate over
     * @param owner   The class node instances that the method belongs to
     * @param oldName Old name to find in class nodes
     * @param newName New name to replace old name with in class nodes
     */
    public static void updateMethodReferences(List<ClassNode> classes, ClassNode owner, String oldName, String newName) {
        renamedMethodReferenceCounter = 0;
        renamedMethodReferenceFileCounter = 0;

        for (ClassNode classNode : classes) {
            int referenceCounter = renamedMethodReferenceCounter;

            // Instructions must be updated first to ensure the real owner can be found
            for (MethodNode methodNode : classNode.methods) {
                // Update instructions
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    // Method references
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        ClassNode realOwnerClassNode = ClassNodeUtil.matchClassNode(classes, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                        boolean updateInstruction = false;

                        if (realOwnerClassNode != null && realOwnerClassNode.name.equals(owner.name) && methodInsnNode.name.equals(oldName)) {
                            updateInstruction = true;
                        } else if (methodInsnNode.owner.equals(owner.name) && methodInsnNode.name.equals(oldName)) {
                            updateInstruction = true;
                        }

                        if (updateInstruction) {
                            methodInsnNode.name = newName;
                            renamedMethodReferenceCounter++;
                        }
                    }
                }
            }

            if (referenceCounter != renamedMethodReferenceCounter) {
                renamedMethodReferenceFileCounter++;
            }
        }

        for (ClassNode classNode : classes) {
            int referenceCounter = renamedMethodReferenceCounter;

            // Update method references
            for (MethodNode methodNode : classNode.methods) {
                // Update method names
                if (classNode.equals(owner) && methodNode.name.equals(oldName)) {
                    methodNode.name = newName;
                    renamedMethodReferenceCounter++;
                }
            }

            if (referenceCounter != renamedMethodReferenceCounter) {
                renamedMethodReferenceFileCounter++;
            }
        }
    }

    /**
     * Update references to the specified field.
     *
     * @param classes List of class nodes to iterate over
     * @param owner   The class node instances that the field belongs to
     * @param oldName Old name to find in class nodes
     * @param newName New name to replace old name with in class nodes
     */
    public static void updateFieldReferences(List<ClassNode> classes, ClassNode owner, String oldName, String newName) {
        renamedFieldReferenceCounter = 0;
        renamedFieldReferenceFileCounter = 0;

        for (ClassNode classNode : classes) {
            int referenceCounter = renamedFieldReferenceCounter;

            // Update field references
            for (FieldNode fieldNode : classNode.fields) {
                if (classNode.equals(owner) && fieldNode.name.equals(oldName)) {
                    fieldNode.name = newName;
                    renamedFieldReferenceCounter++;
                }
            }

            // Update method references
            for (MethodNode methodNode : classNode.methods) {
                // Update instructions
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    // Field references
                    if (insnNode instanceof FieldInsnNode fieldInsnNode) {
                        if (fieldInsnNode.owner.equals(owner.name) && fieldInsnNode.name.equals(oldName)) {
                            fieldInsnNode.name = newName;
                            renamedFieldReferenceCounter++;
                        }
                    }
                }
            }

            if (referenceCounter != renamedFieldReferenceCounter) {
                renamedFieldReferenceFileCounter++;
            }
        }
    }

    public static int getRenamedClassReferenceCounter() {
        return renamedClassReferenceCounter;
    }

    public static int getRenamedClassReferenceFileCounter() {
        return renamedClassReferenceFileCounter;
    }

    public static int getRenamedMethodReferenceCounter() {
        return renamedMethodReferenceCounter;
    }

    public static int getRenamedMethodReferenceFileCounter() {
        return renamedMethodReferenceFileCounter;
    }

    public static int getRenamedFieldReferenceCounter() {
        return renamedFieldReferenceCounter;
    }

    public static int getRenamedFieldReferenceFileCounter() {
        return renamedFieldReferenceFileCounter;
    }
}
