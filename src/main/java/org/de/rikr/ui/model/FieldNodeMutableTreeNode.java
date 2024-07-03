package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class FieldNodeMutableTreeNode extends IconMutableTreeNode {
    private final ClassNode owner;
    private final FieldNode fieldNode;

    public FieldNodeMutableTreeNode(String iconName, ClassNode owner, FieldNode fieldNode, Object userObject) {
        super(iconName, userObject);
        this.owner = owner;
        this.fieldNode = fieldNode;
    }

    public ClassNode getOwner() {
        return owner;
    }

    public FieldNode getFieldNode() {
        return fieldNode;
    }
}
