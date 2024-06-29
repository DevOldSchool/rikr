package org.de.rikr.ui.model;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class IconMutableTreeNode extends DefaultMutableTreeNode {
    private final ImageIcon icon;

    public IconMutableTreeNode(String iconName, Object userObject, ImageIcon icon) {
        super(userObject);
        this.icon = null;
    }
}
