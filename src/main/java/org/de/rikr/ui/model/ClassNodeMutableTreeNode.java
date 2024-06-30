package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

public class ClassNodeMutableTreeNode extends IconMutableTreeNode {
    private final ClassNode classNode;

    public ClassNodeMutableTreeNode(ClassNode classNode, Object userObject) {
        super("class", userObject);
        this.classNode = classNode;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
