package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.tree.DefaultMutableTreeNode;

public class InterfaceNodeMutableTreeNode extends DefaultMutableTreeNode {
    private final ClassNode classNode;

    public InterfaceNodeMutableTreeNode(ClassNode classNode, Object userObject) {
        super(userObject);
        this.classNode = classNode;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
