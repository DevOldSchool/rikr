package org.de.rikr.ui;


import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HierarchyViewer extends JFrame {
    private final HashMap<ClassNode, List<ClassNode>> hierarchyMap;

    public HierarchyViewer(HashMap<ClassNode, List<ClassNode>> hierarchyMap) {
        this.hierarchyMap = hierarchyMap;
        setTitle("Class Hierarchy");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Class Hierarchy");
        populateTree(root);

        JTree tree = new JTree(root);
        JScrollPane treeScrollPane = new JScrollPane(tree);

        add(treeScrollPane);
        setVisible(true);
    }

    private void populateTree(DefaultMutableTreeNode root) {
        for (Map.Entry<ClassNode, List<ClassNode>> entry : hierarchyMap.entrySet()) {
            DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(entry.getKey().name);
            root.add(parentNode);
            addChildren(parentNode, entry.getValue());
        }
    }

    private void addChildren(DefaultMutableTreeNode parentNode, List<ClassNode> children) {
        for (ClassNode child : children) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child.name);
            parentNode.add(childNode);
            List<ClassNode> grandChildren = hierarchyMap.get(child);
            if (grandChildren != null && !grandChildren.isEmpty()) {
                addChildren(childNode, grandChildren);
            }
        }
    }

}