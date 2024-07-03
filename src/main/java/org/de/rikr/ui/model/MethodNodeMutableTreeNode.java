package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodNodeMutableTreeNode extends IconMutableTreeNode {
    private final ClassNode owner;
    private final MethodNode methodNode;

    public MethodNodeMutableTreeNode(String iconName, ClassNode owner, MethodNode methodNode, Object userObject) {
        super(iconName, userObject);
        this.owner = owner;
        this.methodNode = methodNode;
    }

    public ClassNode getOwner() {
        return owner;
    }

    public MethodNode getMethodNode() {
        return methodNode;
    }
}
