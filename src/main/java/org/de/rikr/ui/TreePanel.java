package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class TreePanel extends JScrollPane {
    private final JTree tree;

    public TreePanel(Rikr controller) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JAR Files");
        tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode instanceof ClassNodeMutableTreeNode classNode) {
                controller.displayClassDetails(classNode.getClassNode());
            }
        });

        // Create context menu
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("Remove");
        popupMenu.add(removeItem);

        // Add action listener for Remove menu item
        removeItem.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getParent() != null) {
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
                model.removeNodeFromParent(selectedNode);

                if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
                    controller.removeClass(classNodeMutableTreeNode.getJarName(), classNodeMutableTreeNode.getClassNode());
                } else {
                    controller.removeJar(selectedNode.toString());
                }

                controller.clearContent();
            }
        });

        // Add a custom mouse listener to the JTree
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                    tree.setSelectionRow(row);

                    // Show popup menu only if a valid node is selected
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    if (selectedNode != null) {
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        setViewportView(tree);
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root Node");
        for (String jarName : jarClassesMap.keySet()) {
            DefaultMutableTreeNode jarNode = new DefaultMutableTreeNode(jarName);
            List<ClassNode> classes = jarClassesMap.get(jarName);
            for (ClassNode classNode : classes) {
                String className = classNode.name + ".class";
                jarNode.add(new ClassNodeMutableTreeNode(jarName, classNode, className));
            }
            root.add(jarNode);
        }

        DefaultTreeModel model = new DefaultTreeModel(root);
        tree.setModel(model);
        expandAllNodes();
    }

    private void expandAllNodes() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}
