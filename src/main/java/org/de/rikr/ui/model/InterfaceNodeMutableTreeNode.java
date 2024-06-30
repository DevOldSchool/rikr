package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

public class InterfaceNodeMutableTreeNode extends IconMutableTreeNode {
    private final ClassNode classNode;

    public InterfaceNodeMutableTreeNode(ClassNode classNode, Object userObject) {
        super("interface", userObject);
        this.classNode = classNode;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
