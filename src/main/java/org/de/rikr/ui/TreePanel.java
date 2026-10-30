package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.handler.TreeSelectionHandler;
import org.de.rikr.ui.model.ClassMutableTreeNode;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.de.rikr.ui.model.FieldNodeMutableTreeNode;
import org.de.rikr.ui.model.InterfaceNodeMutableTreeNode;
import org.de.rikr.ui.model.JarMutableTreeNode;
import org.de.rikr.ui.model.MethodNodeMutableTreeNode;
import org.de.rikr.ui.model.PackageTreeNode;
import org.de.rikr.ui.model.TreeNodes;
import org.de.rikr.utilities.ClassNodeUtil;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TreePanel extends JPanel {
    private final Rikr controller;
    private final JTree tree;
    private final TreePopupMenu popupMenu;
    private final DefaultTreeModel treeModel;
    private final TreeSelectionHandler treeSelectionHandler;
    private final JTextField searchField;
    private final MatchOptions matchOptions;
    private boolean matchCase;
    private boolean matchWord;
    private JPanel searchBarPanel;
    private JScrollPane scrollPane;

    public TreePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(null);

        // Create the search bar
        searchBarPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        SearchFields.style(searchField, "Find in the tree");
        matchOptions = new MatchOptions(searchField, options -> {
            matchCase = options.matchCase();
            matchWord = options.matchWord();
            performSearch();
        });
        searchBarPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        searchBarPanel.add(searchField, BorderLayout.CENTER);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JAR Files");
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new IconTreeCellRenderer());
        tree.setBackground(Theme.BACKGROUND_COLOR);
        tree.getAccessibleContext().setAccessibleName("Project classes");
        treeSelectionHandler = new TreeSelectionHandler(controller, tree);
        tree.addTreeSelectionListener(treeSelectionHandler);

        popupMenu = new TreePopupMenu(controller, tree);

        // Add components to the panel
        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);
        add(searchBarPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        if (searchField != null) {
            SearchFields.applyTheme(searchField);
            matchOptions.applyTheme();
        }

        if (searchBarPanel != null) {
            searchBarPanel.setBackground(Theme.BACKGROUND_COLOR);
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

    /**
     * Show the JARs, each class under its package, sorted by name. Folders that were open stay open, and the
     * selection stays, so a rebuild after a rename does not lose the user's place.
     */
    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        Set<Object> expanded = new HashSet<>();
        Enumeration<TreePath> open = tree.getExpandedDescendants(new TreePath(treeModel.getRoot()));
        while (open != null && open.hasMoreElements()) {
            Object key = key((DefaultMutableTreeNode) open.nextElement().getLastPathComponent());
            if (key != null) {
                expanded.add(key);
            }
        }
        DefaultMutableTreeNode selected = getSelectedNode();
        Object selectedKey = selected == null ? null : key(selected);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JAR Files");
        jarClassesMap.keySet().stream().sorted().forEach(jarName -> {
            List<ClassNode> classes = jarClassesMap.get(jarName);
            JarMutableTreeNode jarNode = new JarMutableTreeNode(jarName, classes, jarName);
            addByPackage(jarNode, jarName, classes);
            root.add(jarNode);
        });

        // Restoring the selection must not open the class again.
        tree.removeTreeSelectionListener(treeSelectionHandler);
        treeModel.setRoot(root);
        tree.setModel(treeModel);
        if (expanded.isEmpty()) {
            SwingUtilities.invokeLater(this::expandRootChildren);
        } else {
            Enumeration<TreeNode> nodes = root.depthFirstEnumeration();
            List<TreePath> toOpen = new ArrayList<>();
            TreePath toSelect = null;
            while (nodes.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
                Object key = key(node);
                if (key != null && expanded.contains(key)) {
                    toOpen.add(new TreePath(node.getPath()));
                }
                if (key != null && key.equals(selectedKey)) {
                    toSelect = new TreePath(node.getPath());
                }
            }
            toOpen.forEach(tree::expandPath);
            if (toSelect != null) {
                tree.setSelectionPath(toSelect);
            }
        }
        tree.addTreeSelectionListener(treeSelectionHandler);
    }

    /**
     * Add the classes under one node per package, packages first, then classes in no package; all sorted.
     */
    private static void addByPackage(DefaultMutableTreeNode parent, String jarName, List<ClassNode> classes) {
        Map<String, List<ClassNode>> byPackage = new TreeMap<>();
        for (ClassNode classNode : classes) {
            int slash = classNode.name.lastIndexOf('/');
            byPackage.computeIfAbsent(slash < 0 ? "" : classNode.name.substring(0, slash), name -> new ArrayList<>())
                .add(classNode);
        }
        Comparator<ClassNode> byName = Comparator.comparing(classNode -> classNode.name);
        byPackage.forEach((packageName, members) -> {
            if (packageName.isEmpty()) {
                return;
            }
            PackageTreeNode packageNode = new PackageTreeNode(packageName);
            members.stream().sorted(byName).forEach(classNode ->
                packageNode.add(new ClassMutableTreeNode(jarName, classNode, simpleName(classNode))));
            parent.add(packageNode);
        });
        byPackage.getOrDefault("", List.of()).stream().sorted(byName).forEach(classNode ->
            parent.add(new ClassMutableTreeNode(jarName, classNode, classNode.name)));
    }

    private static String simpleName(ClassNode classNode) {
        return classNode.name.substring(classNode.name.lastIndexOf('/') + 1);
    }

    /**
     * What a node stands for, so the same place can be found again in a rebuilt tree.
     */
    private static Object key(DefaultMutableTreeNode node) {
        return switch (node) {
            case JarMutableTreeNode jar -> "jar:" + jar.getJarName();
            case PackageTreeNode packageNode -> "package:" + TreeNodes.jarOf(packageNode).orElse("")
                + ":" + packageNode.getPackageName();
            case ClassMutableTreeNode classNode -> classNode.getClassNode();
            case ClassNodeMutableTreeNode classNode -> List.of("declaration", classNode.getClassNode());
            case InterfaceNodeMutableTreeNode classNode -> List.of("declaration", classNode.getClassNode());
            case FieldNodeMutableTreeNode field -> field.getFieldNode();
            case MethodNodeMutableTreeNode method -> method.getMethodNode();
            default -> null;
        };
    }

    /**
     * Select a class in the tree and scroll to it, which also opens it in the code view.
     *
     * @return false if the class is not in the tree
     */
    /**
     * Select the class in the tree without opening it again, unless the selection is already in that class, so the
     * tree shows the class opened from search, references or code.
     */
    public void follow(ClassNode classNode) {
        for (TreeNode node = treeSelectionHandler.getSelectedNode(); node != null; node = node.getParent()) {
            if (node instanceof ClassMutableTreeNode classNodeInTree && classNodeInTree.getClassNode() == classNode) {
                return;
            }
        }
        treeSelectionHandler.selectQuietly(() -> reveal(classNode));
    }

    public boolean reveal(ClassNode classNode) {
        Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) treeModel.getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            if (nodes.nextElement() instanceof ClassMutableTreeNode node && node.getClassNode() == classNode) {
                TreePath path = new TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return true;
            }
        }
        return false;
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap, boolean groupBySuperclass, boolean groupByInterfaces) {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();

        if (rootNode == null) {
            return;
        }

        for (String jarName : jarClassesMap.keySet()) {
            JarMutableTreeNode jarNode = null;
            for (int index = 0; index < rootNode.getChildCount(); index++) {
                if (rootNode.getChildAt(index) instanceof JarMutableTreeNode candidate
                    && candidate.getJarName().equals(jarName)) {
                    jarNode = candidate;
                }
            }

            if (jarNode == null) {
                continue;
            }

            List<ClassNode> classes = jarNode.getClasses();
            Map<String, List<ClassNode>> classGroups = Map.of();

            if (groupBySuperclass) {
                classGroups = ClassNodeUtil.groupBySuperclass(classes);
            } else if (groupByInterfaces) {
                classGroups = ClassNodeUtil.groupByInterface(classes);
            } else {
                // Remove grouping
                jarNode.removeAllChildren();
                addByPackage(jarNode, jarName, classes);
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

    /**
     * Update the labels of classes, fields and methods to their current names. A rename can change names and
     * descriptors in many classes, so every node is checked. The tree keeps its expansion and selection.
     */
    public void refreshLabels() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        if (root == null) {
            return;
        }
        Enumeration<TreeNode> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            String label = switch (node) {
                // Under a package node a class shows its simple name; elsewhere its full name.
                case ClassMutableTreeNode classNode -> classNode.getParent() instanceof PackageTreeNode
                    ? simpleName(classNode.getClassNode()) : classNode.getClassNode().name;
                case ClassNodeMutableTreeNode classNode -> classNode.getClassNode().name;
                case InterfaceNodeMutableTreeNode classNode -> classNode.getClassNode().name;
                case FieldNodeMutableTreeNode field -> field.getFieldNode().name + " " + field.getFieldNode().desc;
                case MethodNodeMutableTreeNode method -> method.getMethodNode().name + " " + method.getMethodNode().desc;
                default -> null;
            };
            if (label != null && !label.equals(String.valueOf(node.getUserObject()))) {
                node.setUserObject(label);
                treeModel.nodeChanged(node);
            }
        }
    }

    public void updateDirtyJars(Set<String> dirtyJars) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        if (root == null) {
            return;
        }
        for (int index = 0; index < root.getChildCount(); index++) {
            if (root.getChildAt(index) instanceof JarMutableTreeNode jarNode) {
                jarNode.setDirty(dirtyJars.contains(jarNode.getJarName()));
                treeModel.nodeChanged(jarNode);
            }
        }
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

            DefaultMutableTreeNode parentNode = new ClassMutableTreeNode(jarName, parentClassNode, name);

            for (ClassNode classNode : classGroup) {
                parentNode.add(new ClassMutableTreeNode(jarName, classNode, classNode.name));
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
