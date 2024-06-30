package org.de.rikr.ui;

import org.de.rikr.ui.model.IconMutableTreeNode;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class IconTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof IconMutableTreeNode iconMutableTreeNode) {
            setIcon(iconMutableTreeNode.getIcon());
        }

        return this;
    }
}
