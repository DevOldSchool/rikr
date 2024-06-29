package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.tree.DefaultMutableTreeNode;

public class ClassNodeMutableTreeNode extends DefaultMutableTreeNode {
    private final ClassNode classNode;

    public ClassNodeMutableTreeNode(ClassNode classNode, Object userObject) {
        super(userObject);
        this.classNode = classNode;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
