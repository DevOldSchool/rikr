package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.tree.DefaultMutableTreeNode;

public class ClassNodeMutableTreeNode extends DefaultMutableTreeNode {
    private final String jarName;
    private final ClassNode classNode;

    public ClassNodeMutableTreeNode(String jarName, ClassNode classNode, Object userObject) {
        super(userObject);
        this.jarName = jarName;
        this.classNode = classNode;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getJarName() {
        return jarName;
    }
}
