package org.de.rikr.ui.model;

import org.de.rikr.ui.Images;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class IconMutableTreeNode extends DefaultMutableTreeNode {
    private final String iconName;

    public IconMutableTreeNode(String iconName, Object userObject) {
        super(userObject);
        this.iconName = iconName;
    }

    public ImageIcon getIcon() {
        return Images.getImage(iconName);
    }
}
