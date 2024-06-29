package org.de.rikr.ui.model;

import org.objectweb.asm.tree.MethodNode;

import javax.swing.tree.DefaultMutableTreeNode;

public class MethodNodeMutableTreeNode extends DefaultMutableTreeNode {
    private final MethodNode methodNode;

    public MethodNodeMutableTreeNode(MethodNode methodNode, Object userObject) {
        super(userObject);
        this.methodNode = methodNode;
    }

    public MethodNode getMethodNode() {
        return methodNode;
    }
}
