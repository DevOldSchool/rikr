package org.de.rikr.ui.model;

import org.de.rikr.ui.ClassNodeImages;
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
            classTreeNode = new InterfaceNodeMutableTreeNode(jarName, classNode, classNode.name);
        } else {
            classTreeNode = new ClassNodeMutableTreeNode(jarName, classNode, classNode.name);
        }

        addFields(classTreeNode);
        addMethods(classTreeNode);

        this.add(classTreeNode);
    }

    private void addFields(DefaultMutableTreeNode classTreeNode) {
        for (FieldNode fieldNode : classNode.fields) {
            classTreeNode.add(new FieldNodeMutableTreeNode(ClassNodeImages.getFieldNodeImageName(fieldNode), classNode, fieldNode, fieldNode.name + " " + fieldNode.desc));
        }
    }

    private void addMethods(DefaultMutableTreeNode classTreeNode) {
        for (MethodNode methodNode : classNode.methods) {
            classTreeNode.add(new MethodNodeMutableTreeNode(ClassNodeImages.getMethodNodeImageName(methodNode), classNode, methodNode, methodNode.name + " " + methodNode.desc));
        }
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getJarName() {
        return jarName;
    }
}
