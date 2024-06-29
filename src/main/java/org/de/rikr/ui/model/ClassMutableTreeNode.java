package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.tree.DefaultMutableTreeNode;
import java.lang.reflect.Modifier;

public class ClassMutableTreeNode extends DefaultMutableTreeNode {
    private final String jarName;
    private final ClassNode classNode;

    public ClassMutableTreeNode(String jarName, ClassNode classNode, Object userObject) {
        super(userObject);
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
            classTreeNode.add(new FieldNodeMutableTreeNode(fieldNode, fieldNode.name + " " + fieldNode.desc));
        }
    }

    private void addMethods(DefaultMutableTreeNode classTreeNode) {
        for (MethodNode methodNode : classNode.methods) {
            classTreeNode.add(new MethodNodeMutableTreeNode(methodNode, methodNode.name + " " + methodNode.desc));
        }
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getJarName() {
        return jarName;
    }
}
