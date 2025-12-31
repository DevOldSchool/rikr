package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.resources.Images;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.handler.TreeSelectionHandler;
import org.de.rikr.ui.model.ClassMutableTreeNode;
import org.de.rikr.ui.model.JarMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public class TreePanel extends JPanel {
    private final Rikr controller;
    private final JTree tree;
    private final TreePopupMenu popupMenu;
    private final DefaultTreeModel treeModel;
    private final TreeSelectionHandler treeSelectionHandler;
    private final JTextField searchField;
    private final JButton matchCaseButton;
    private final JButton matchWordButton;
    private boolean matchCase;
    private boolean matchWord;
    private Color defaultBackgroundColor;
    private JPanel buttonsPanel;
    private JPanel searchBarPanel;
    private JScrollPane scrollPane;

    public TreePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(null);

        // Create the search bar
        searchBarPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        searchField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        matchCaseButton = new JButton(Images.getImage("match-case"));
        matchCaseButton.setToolTipText("Match Case");
        matchCaseButton.setBorderPainted(false);
        matchWordButton = new JButton(Images.getImage("match-word"));
        matchWordButton.setToolTipText("Match Words");
        matchWordButton.setBorderPainted(false);

        // Create a panel for the buttons
        buttonsPanel = new JPanel();
        buttonsPanel.setBackground(Theme.BACKGROUND_COLOR);
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(matchCaseButton);
        buttonsPanel.add(matchWordButton);

        // Add components to the main panel
        searchBarPanel.add(searchField, BorderLayout.CENTER);
        searchBarPanel.add(buttonsPanel, BorderLayout.EAST);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JAR Files");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new IconTreeCellRenderer());
        tree.setBackground(Theme.BACKGROUND_COLOR);
        treeSelectionHandler = new TreeSelectionHandler(controller, tree);
        tree.addTreeSelectionListener(treeSelectionHandler);

        popupMenu = new TreePopupMenu(controller, tree);

        // Add components to the panel
        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);
        add(searchBarPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        defaultBackgroundColor = matchCaseButton.getBackground();
        matchCase = false;
        matchWord = false;

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        if (searchBarPanel != null) {
            searchBarPanel.setBackground(Theme.BACKGROUND_COLOR);
        }

        if (buttonsPanel != null) {
            buttonsPanel.setBackground(Theme.BACKGROUND_COLOR);
        }

        if (tree != null) {
            tree.setBackground(Theme.BACKGROUND_COLOR);
        }

        if (scrollPane != null) {
            scrollPane.getViewport().setBackground(Theme.BACKGROUND_COLOR);

            scrollPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, PanelUtil.createCorner());
            scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, PanelUtil.createCorner());
            scrollPane.setCorner(JScrollPane.LOWER_LEFT_CORNER, PanelUtil.createCorner());
            scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, PanelUtil.createCorner());

            // Match scrollbars to track color for seamless corners
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            if (vsb != null) {
                vsb.setBackground(SettingsManager.getScrollBarTrackColor());
            }
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            if (hsb != null) {
                hsb.setBackground(SettingsManager.getScrollBarTrackColor());
            }
        }

        defaultBackgroundColor = matchCaseButton.getBackground();
        matchCaseButton.setBackground(matchCase ? Theme.SEARCH_BUTTON_SELECTED_COLOR : defaultBackgroundColor);
        matchWordButton.setBackground(matchWord ? Theme.SEARCH_BUTTON_SELECTED_COLOR : defaultBackgroundColor);

        revalidate();
        repaint();
    }

    public void init() {
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                performSearch();
            }
        });

        searchField.addActionListener(e -> performSearch());

        matchCaseButton.addActionListener(e -> {
            matchCase = !matchCase;
            toggleButton(matchCaseButton, matchCase);
        });

        matchWordButton.addActionListener(e -> {
            matchWord = !matchWord;
            toggleButton(matchWordButton, matchWord);
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

        popupMenu.init();
    }

    public ClassNode getNode(List<ClassNode> classes, String superName) {
        for (ClassNode classNode : classes) {
            if (classNode.name.equals(superName)) {
                return classNode;
            }
        }

        return null;
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root Node");
        for (String jarName : jarClassesMap.keySet()) {
            List<ClassNode> classes = jarClassesMap.get(jarName);
            JarMutableTreeNode jarNode = new JarMutableTreeNode(jarName, classes, jarName);

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

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap, boolean groupBySuperclass, boolean groupByInterfaces) {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();

        if (rootNode == null) {
            return;
        }

        for (String jarName : jarClassesMap.keySet()) {
            JarMutableTreeNode jarNode = (JarMutableTreeNode) findChild(rootNode, jarName);

            if (jarNode == null) {
                continue;
            }

            List<ClassNode> classes = jarNode.getClasses();
            Map<String, List<ClassNode>> classGroups = Map.of();

            if (groupBySuperclass) {
                classGroups = controller.getProcessor().groupBySuperclass(classes);
            } else if (groupByInterfaces) {
                classGroups = controller.getProcessor().groupByInterface(classes);
            } else {
                // Remove grouping
                jarNode.removeAllChildren();

                for (ClassNode classNode : classes) {
                    String className = classNode.name + ".class";
                    jarNode.add(new ClassMutableTreeNode(jarName, classNode, className));
                }

                updateNode(jarNode);
            }

            if (groupBySuperclass || groupByInterfaces) {
                addTreeNodes(jarNode, jarName, classes, classGroups);
            }
        }
    }

    public void updateNode(DefaultMutableTreeNode node) {
        // Notify the tree model that the node has changed
        DefaultTreeModel defaultTreeModel = (DefaultTreeModel) tree.getModel();
        defaultTreeModel.nodeChanged(node);
        treeModel.nodeStructureChanged(node);

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
        return treeSelectionHandler.getSelectedNode();
    }

    public DefaultMutableTreeNode findChild(DefaultMutableTreeNode node, String name) {
        if (node == null || name == null) {
            return null;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);

            if (name.equals(childNode.getUserObject().toString())) {
                return childNode;
            }
        }

        return null;
    }

    public DefaultMutableTreeNode findChildRecursively(DefaultMutableTreeNode node, String name) {
        if (node == null || name == null) {
            return null;
        }

        // Check if the current node matches the name
        if (name.equals(node.getUserObject().toString())) {
            return node;
        }

        // Iterate over the children and search recursively
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode result = findChildRecursively(childNode, name);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    public void addTreeNodes(DefaultMutableTreeNode selectedNode, String jarName, List<ClassNode> classes, Map<String, List<ClassNode>> classGroups) {
        selectedNode.removeAllChildren();

        for (String name : classGroups.keySet()) {
            List<ClassNode> classGroup = classGroups.get(name);
            ClassNode parentClassNode = controller.getUserInterface().getTreePanel().getNode(classes, name);

            if (parentClassNode == null) {
                continue;
            }

            DefaultMutableTreeNode parentNode = new ClassMutableTreeNode(jarName, parentClassNode, name + ".class");

            for (ClassNode classNode : classGroup) {
                String className = classNode.name + ".class";
                parentNode.add(new ClassMutableTreeNode(jarName, classNode, className));
            }

            selectedNode.add(parentNode);
        }

        controller.getUserInterface().getTreePanel().updateNode(selectedNode);
    }

    public void performSearch() {
        String query = searchField.getText();

        // Reset to default
        if (query.isEmpty()) {
            tree.clearSelection();
            tree.setModel(treeModel);
        }

        if (query.length() < 2) {
            return;
        }

        if (!matchCase) {
            query = query.toLowerCase();
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> e = root.breadthFirstEnumeration();

        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (areEqualStrings(node.getUserObject().toString(), query)) {
                TreeNode[] pathToNode = node.getPath();
                TreePath treePath = new TreePath(pathToNode);
                tree.scrollPathToVisible(treePath);
                tree.setSelectionPath(treePath);
                break;
            }
        }
    }

    private void toggleButton(JButton button, boolean toggle) {
        if (toggle) {
            button.setBackground(Theme.SEARCH_BUTTON_SELECTED_COLOR);
        } else {
            button.setBackground(defaultBackgroundColor);
        }

        performSearch();
    }

    private boolean areEqualStrings(String first, String second) {
        if (!matchCase) {
            first = first.toLowerCase();
            second = second.toLowerCase();
        }

        if (matchWord) {
            return first.equals(second);
        }

        return first.contains(second);
    }

    public void select() {
        int length = searchField.getText() != null ? searchField.getText().length() : 0;
        searchField.setCaretPosition(length);
        searchField.moveCaretPosition(length);
        searchField.requestFocusInWindow();
    }
}
