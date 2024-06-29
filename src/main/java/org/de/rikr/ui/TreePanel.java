package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.*;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class TreePanel extends JScrollPane {
    private final Rikr controller;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final JPopupMenu popupMenu;
    private final JMenuItem removeItem;
    private final JMenuItem renameItem;
    private DefaultMutableTreeNode selectedNode;

    public TreePanel(Rikr controller) {
        this.controller = controller;

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JAR Files");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(e -> {
            selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

            if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                controller.displayBytecode(classMutableTreeNode.getClassNode());
            }
            // TODO add support for ClassNode selection which should highlight the class in the content pane
            // TODO add support for FieldNode selection which should highlight the field in the content pane
            // TODO add support for MethodNode selection which should highlight the method in the content pane
        });

        // Create context menu
        popupMenu = new JPopupMenu();
        removeItem = new JMenuItem("Remove");
        renameItem = new JMenuItem("Rename");
        popupMenu.add(removeItem);
        popupMenu.add(renameItem);

        setViewportView(tree);
    }

    public void init() {
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

        // Add action listener for Remove menu item
        removeItem.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getParent() != null) {
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

                if (selectedNode instanceof ClassNodeMutableTreeNode |
                        selectedNode instanceof InterfaceNodeMutableTreeNode |
                        selectedNode instanceof FieldNodeMutableTreeNode |
                        selectedNode instanceof MethodNodeMutableTreeNode) {
                    controller.log("Unable to remove child node.");
                } else if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                    model.removeNodeFromParent(selectedNode);
                    controller.removeClass(classMutableTreeNode.getJarName(), classMutableTreeNode.getClassNode());
                    controller.clearContent();
                } else {
                    model.removeNodeFromParent(selectedNode);
                    controller.removeJar(selectedNode.toString());
                    controller.clearContent();
                }
            }
        });

        // Add action listener for Rename menu item
        renameItem.addActionListener(controller.getContentPanel().getRenameActionHandler());
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root Node");
        for (String jarName : jarClassesMap.keySet()) {
            DefaultMutableTreeNode jarNode = new DefaultMutableTreeNode(jarName);
            List<ClassNode> classes = jarClassesMap.get(jarName);
            for (ClassNode classNode : classes) {
                String className = classNode.name + ".class";
                jarNode.add(new ClassMutableTreeNode(jarName, classNode, className));
            }
            root.add(jarNode);
        }

        treeModel.setRoot(root);
        tree.setModel(treeModel);

        SwingUtilities.invokeLater(this::expandRootChildren);
    }

    public void updateNode(DefaultMutableTreeNode node) {
        // Notify the tree model that the node has changed
        DefaultTreeModel defaultTreeModel = (DefaultTreeModel) tree.getModel();
        defaultTreeModel.nodeChanged(node);

        // Ensure the full path is used to select the node
        TreePath path = new TreePath(node.getPath());
        selectNode(path);
    }

    public void selectNode(TreePath path) {
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    private void expandRootChildren() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();

        // Expand immediate children of the root
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) root.getChildAt(i);
            TreePath path = new TreePath(childNode.getPath());
            expandNode(tree, path);
        }
    }

    public JTree getTree() {
        return tree;
    }
  
    private void expandNode(JTree tree, TreePath path) {
        tree.expandPath(path);

        TreeNode node = (TreeNode) path.getLastPathComponent();
        if (node.getChildCount() > 0) {
            for (int i = 0; i < node.getChildCount(); i++) {
                TreeNode childNode = node.getChildAt(i);
                TreePath childPath = path.pathByAddingChild(childNode);
                tree.collapsePath(childPath);
            }
        }
    }

    public DefaultMutableTreeNode getSelectedNode() {
        return selectedNode;
    }
}
