package org.de.rikr.ui.model;

import org.objectweb.asm.tree.MethodNode;

public class MethodNodeMutableTreeNode extends IconMutableTreeNode {
    private final MethodNode methodNode;

    public MethodNodeMutableTreeNode(String iconName, MethodNode methodNode, Object userObject) {
        super(iconName, userObject);
        this.methodNode = methodNode;
    }

    public MethodNode getMethodNode() {
        return methodNode;
    }
}
