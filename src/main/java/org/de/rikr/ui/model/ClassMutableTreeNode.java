package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.tree.DefaultMutableTreeNode;
import java.lang.reflect.Modifier;

public class ClassMutableTreeNode extends IconMutableTreeNode {
    private final String jarName;
    private final ClassNode classNode;

    public ClassMutableTreeNode(String jarName, ClassNode classNode, Object userObject) {
        super("class_binary", userObject);
        this.jarName = jarName;
        this.classNode = classNode;
        this.addChildren();
    }

    public void addChildren() {
        DefaultMutableTreeNode classTreeNode;

        if (Modifier.isInterface(classNode.access)) {
            classTreeNode = new InterfaceNodeMutableTreeNode(classNode, classNode.name);
        } else {
            classTreeNode = new ClassNodeMutableTreeNode(classNode, classNode.name);
        }

        addFields(classTreeNode);
        addMethods(classTreeNode);

        this.add(classTreeNode);
    }

    private void addFields(DefaultMutableTreeNode classTreeNode) {
        for (FieldNode fieldNode : classNode.fields) {
            String iconName;

            if (Modifier.isPublic(fieldNode.access)) {
                iconName = "field_public";
            } else if (Modifier.isPrivate(fieldNode.access)) {
                iconName = "field_private";
            } else if (Modifier.isProtected(fieldNode.access)) {
                iconName = "field_protected";
            } else {
                iconName = "field_default";
            }

            if (Modifier.isAbstract(fieldNode.access)) {
                iconName += "_abstract";
            }
            if (Modifier.isStatic(fieldNode.access)) {
                iconName += "_static";
            }
            if (Modifier.isFinal(fieldNode.access)) {
                iconName += "_final";
            }

            classTreeNode.add(new FieldNodeMutableTreeNode(iconName, fieldNode, fieldNode.name + " " + fieldNode.desc));
        }
    }

    private void addMethods(DefaultMutableTreeNode classTreeNode) {
        for (MethodNode methodNode : classNode.methods) {
            String iconName;

            if (Modifier.isPublic(methodNode.access)) {
                iconName = "method_public";
            } else if (Modifier.isPrivate(methodNode.access)) {
                iconName = "method_private";
            } else if (Modifier.isProtected(methodNode.access)) {
                iconName = "method_protected";
            } else {
                iconName = "method_default";
            }

            if (Modifier.isAbstract(methodNode.access)) {
                iconName += "_abstract";
            }
            if (Modifier.isStatic(methodNode.access)) {
                iconName += "_static";
            }
            if (Modifier.isFinal(methodNode.access)) {
                iconName += "_final";
            }

            classTreeNode.add(new MethodNodeMutableTreeNode(iconName, methodNode, methodNode.name + " " + methodNode.desc));
        }
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getJarName() {
        return jarName;
    }
}
