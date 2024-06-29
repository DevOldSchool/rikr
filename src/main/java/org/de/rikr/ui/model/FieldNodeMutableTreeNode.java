package org.de.rikr.ui.model;

import org.objectweb.asm.tree.FieldNode;

import javax.swing.tree.DefaultMutableTreeNode;

public class FieldNodeMutableTreeNode extends DefaultMutableTreeNode {
    private final FieldNode fieldNode;

    public FieldNodeMutableTreeNode(FieldNode fieldNode, Object userObject) {
        super(userObject);
        this.fieldNode = fieldNode;
    }

    public FieldNode getFieldNode() {
        return fieldNode;
    }
}
