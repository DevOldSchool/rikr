package org.de.rikr.ui.model;

import org.objectweb.asm.tree.FieldNode;

public class FieldNodeMutableTreeNode extends IconMutableTreeNode {
    private final FieldNode fieldNode;

    public FieldNodeMutableTreeNode(String iconName, FieldNode fieldNode, Object userObject) {
        super(iconName, userObject);
        this.fieldNode = fieldNode;
    }

    public FieldNode getFieldNode() {
        return fieldNode;
    }
}
