package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;
import org.de.rikr.settings.Settings;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.model.ClassMutableTreeNode;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.de.rikr.ui.model.JarMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ClassViewer {
    private final JFrame frame;
    private final Rikr controller;
    private final MenuBar menuBar;
    private final JSplitPane mainSplitPane;
    private final JSplitPane contentSplitPane;
    private final JSplitPane verticalSplitPane;
    private final NavigationPanel navigationPanel;
    private final TreePanel treePanel;
    private final SearchBar searchBar;
    private final ContentPanel contentPanelLeft;
    private final ContentPanel contentPanelRight;
    private JSplitPane asmJavaSplitPane;
    private boolean asmJavaSplitEnabled = false;
    private ClassNode lastDisplayedClassNode;
    private final LogPanel logPanel;
    private final JPanel projectPanel;
    private final SearchPanel searchPanel;
    private final MethodSimulatorPanel methodSimulatorPanel;
    private final JPanel rightPanel;
    private final SimulatorBar simulatorBar;

    public ClassViewer(Rikr controller) {
        this.controller = controller;
        frame = new JFrame("Rikr");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

        if (SystemInfo.isMacOS) {
            if (SystemInfo.isMacFullWindowContentSupported) {
                frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);

                // hide window title
                if (SystemInfo.isJava_17_orLater) {
                    frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
                } else {
                    frame.setTitle(null);
                }
            }

            // enable full screen mode for this window (for Java 8 - 10; not necessary for Java 11+)
            if (!SystemInfo.isJava_11_orLater) {
                frame.getRootPane().putClientProperty("apple.awt.fullscreenable", true);
            }
        }

        // Initialize panels
        treePanel = new TreePanel(controller);
        contentPanelLeft = new ContentPanel(controller);
        contentPanelRight = new ContentPanel(controller, true); // Java-only panel
        searchBar = new SearchBar(controller, e -> toggleSearchBarVisibility(false));
        logPanel = new LogPanel();
        projectPanel = new JPanel(new BorderLayout());
        projectPanel.add(treePanel, BorderLayout.CENTER);
        searchPanel = new SearchPanel(controller);
        methodSimulatorPanel = new MethodSimulatorPanel(controller);
        simulatorBar = new SimulatorBar(controller, e -> toggleMethodSimulatorVisibility(methodSimulatorPanel.isVisible()));

        // Create a panel to hold both the search panel and content panel
        rightPanel = new JPanel(new BorderLayout());

        rightPanel.add(searchBar, BorderLayout.NORTH);
        contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contentPanelLeft, methodSimulatorPanel);
        contentSplitPane.setDividerLocation(700);
        contentSplitPane.setResizeWeight(0.80);
        rightPanel.add(contentSplitPane, BorderLayout.CENTER);
        rightPanel.add(simulatorBar, BorderLayout.SOUTH);

        // Create the navigation panel
        navigationPanel = new NavigationPanel(
            controller,
            e -> showProjectPanel(),
            e -> showSearchPanel()
        );

        // Create a split pane for the navigation panel and tree panel
        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        leftSplitPane.setLeftComponent(navigationPanel);
        leftSplitPane.setRightComponent(projectPanel);
        leftSplitPane.setDividerSize(1);
        leftSplitPane.setEnabled(false);

        // Make the divider invisible
        leftSplitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                    }
                };
            }
        });

        // Create the main split pane
        mainSplitPane = new SplitPane(JSplitPane.HORIZONTAL_SPLIT, 10);
        mainSplitPane.setLeftComponent(leftSplitPane);
        mainSplitPane.setRightComponent(rightPanel);
        mainSplitPane.setDividerLocation(300);
        mainSplitPane.setDividerSize(1);

        // Create the vertical split pane
        verticalSplitPane = new SplitPane(JSplitPane.VERTICAL_SPLIT, 10);
        verticalSplitPane.setTopComponent(mainSplitPane);
        verticalSplitPane.setBottomComponent(logPanel);
        verticalSplitPane.setDividerLocation(600);
        verticalSplitPane.setDividerSize(0);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(verticalSplitPane, BorderLayout.CENTER);

        frame.add(panel);

        // Initialize menu bar
        menuBar = new MenuBar(
            controller,
            e -> openFileDialog(),
            e -> toggleSearchBarVisibility(false),
            e -> showProjectTab(),
            e -> showGlobalSearchTab(),
            e -> toggleLogVisibility(logPanel.isVisible()),
            e -> saveSelectedClass(),
            e -> saveSelectedJar(),
            e -> overwriteOriginalJar(),
            e -> openSettingsDialog()
        );
        frame.setJMenuBar(menuBar);

        // Put Preferences under the app menu
        if (SystemInfo.isMacOS && java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            try {
                if (desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) {
                    desktop.setPreferencesHandler(e -> openSettingsDialog());
                }
            } catch (UnsupportedOperationException ignored) {
            }
        }

        // Wire split view toggle
        menuBar.getSplitAsmJavaItem().addActionListener(e -> {
            boolean selected = menuBar.getSplitAsmJavaItem().isSelected();
            asmJavaSplitEnabled = selected;
            if (selected) {
                asmJavaSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contentPanelLeft, contentPanelRight);
                asmJavaSplitPane.setResizeWeight(0.5);
                asmJavaSplitPane.setDividerSize(5);
                contentSplitPane.setLeftComponent(asmJavaSplitPane);

                SettingsManager.refreshThemeFor(contentPanelRight);
                SettingsManager.updateComponentTree(asmJavaSplitPane);
                if (lastDisplayedClassNode != null) {
                    contentPanelRight.displayBytecode(lastDisplayedClassNode);
                }
            } else {
                contentSplitPane.setLeftComponent(contentPanelLeft);
                asmJavaSplitPane = null;
            }
            frame.revalidate();
            frame.repaint();
        });

        // Search toggle
        InputMap inputMap = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = frame.getRootPane().getActionMap();

        String toggleSearchPanelAction = "toggleSearchPanel";
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), toggleSearchPanelAction);
        actionMap.put(toggleSearchPanelAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleSearchBarVisibility(true);
            }
        });

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        try {
            if (menuBar != null) {
                menuBar.setOpaque(true);
                menuBar.setBackground(Theme.BACKGROUND_COLOR);
            }

            if (projectPanel != null) {
                projectPanel.setBackground(Theme.BACKGROUND_COLOR);
            }

            if (rightPanel != null) {
                rightPanel.setBackground(Theme.BACKGROUND_COLOR);
            }

            if (frame != null) {
                frame.revalidate();
                frame.repaint();
            }
        } catch (Exception ignored) {
        }
    }

    public void init() {
        contentPanelLeft.init();
        contentPanelRight.init();
        navigationPanel.init();
        treePanel.init();
        searchBar.init();
        logPanel.init();
        menuBar.init();
        searchPanel.init();
        simulatorBar.init();

        SwingUtilities.invokeLater(() -> {
            toggleLogVisibility(logPanel.isVisible());
            toggleMethodSimulatorVisibility(methodSimulatorPanel.isVisible());

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            searchBar.setVisible(false);

            contentPanelLeft.getContentPane().grabFocus();
        });
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        SwingUtilities.invokeLater(() -> treePanel.updateTree(jarClassesMap));
    }

    public void displayBytecode(ClassNode classNode) {
        lastDisplayedClassNode = classNode;

        contentPanelLeft.displayBytecode(classNode);
        if (asmJavaSplitEnabled) {
            contentPanelRight.displayBytecode(classNode);
        }
    }

    public void clearContent() {
        contentPanelLeft.clear();
        contentPanelRight.clear();
    }

    private void showProjectPanel() {
        JSplitPane leftOfTop = (JSplitPane) ((JSplitPane) verticalSplitPane.getTopComponent()).getLeftComponent();
        leftOfTop.setRightComponent(projectPanel);
        SettingsManager.refreshThemeFor(projectPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showSearchPanel() {
        JSplitPane leftOfTop = (JSplitPane) ((JSplitPane) verticalSplitPane.getTopComponent()).getLeftComponent();
        leftOfTop.setRightComponent(searchPanel);
        SettingsManager.refreshThemeFor(searchPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void openFileDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(frame);

        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            for (File file : selectedFiles) {
                if (file.getName().endsWith(".jar")) {
                    controller.loadJarFiles(new File[]{file});
                } else if (file.getName().endsWith(".class")) {
                    controller.loadClassFiles(new File[]{file});
                }
            }
        }
    }

    private void toggleLogVisibility(boolean isVisible) {
        if (isVisible) {
            verticalSplitPane.setBottomComponent(null);
            verticalSplitPane.setDividerSize(0);
            logPanel.setVisible(false);
        } else {
            verticalSplitPane.setBottomComponent(logPanel);
            verticalSplitPane.setDividerLocation(600);
            verticalSplitPane.setDividerSize(1);
            logPanel.setVisible(true);
            SettingsManager.refreshThemeFor(logPanel);
        }
    }

    public void toggleMethodSimulatorVisibility(boolean isVisible) {
        if (isVisible) {
            contentSplitPane.setRightComponent(null);
            contentSplitPane.setDividerSize(0);
            methodSimulatorPanel.setVisible(false);
            toggleSimulatorBarVisibility(false);
        } else {
            contentSplitPane.setRightComponent(methodSimulatorPanel);
            contentSplitPane.setDividerLocation(700);
            contentSplitPane.setDividerSize(1);
            methodSimulatorPanel.setVisible(true);
            toggleSimulatorBarVisibility(true);
        }
    }

    private void toggleSearchBarVisibility(boolean isVisible) {
        searchBar.setVisible(isVisible);
        searchBar.focus();
        frame.revalidate();
        frame.repaint();
    }

    private void toggleSimulatorBarVisibility(boolean isVisible) {
        simulatorBar.setVisible(isVisible);
        frame.revalidate();
        frame.repaint();
    }

    private void showProjectTab() {
        navigationPanel.selectProjectTab();
    }

    private void showGlobalSearchTab() {
        navigationPanel.selectSearchTab();
        searchPanel.select();
    }

    public void setStackAreaText(Stack<Object> stack) {
        methodSimulatorPanel.setStackAreaText(stack);
    }

    public void setLocalVariableAreaText(Map<Integer, Object> localVariables) {
        methodSimulatorPanel.setLocalVariableAreaText(localVariables);
    }

    public LogPanel getLogPanel() {
        return logPanel;
    }

    public ContentPanel getContentPanel() {
        return contentPanelLeft;
    }

    public JTextPane getContentPane() {
        return contentPanelLeft.getContentPane();
    }

    public TreePanel getTreePanel() {
        return treePanel;
    }

    public MethodSimulatorPanel getMethodSimulatorPanel() {
        return methodSimulatorPanel;
    }

    public JFrame getFrame() {
        return frame;
    }

    private void saveSelectedClass() {
        DefaultMutableTreeNode node = getTreePanel().getSelectedNode();
        if (node == null) {
            JOptionPane.showMessageDialog(frame, "No selection.", "Save Class", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ClassNode classNode = null;
        if (node instanceof ClassMutableTreeNode) {
            classNode = ((ClassMutableTreeNode) node).getClassNode();
        } else if (node instanceof ClassNodeMutableTreeNode) {
            classNode = ((ClassNodeMutableTreeNode) node).getClassNode();
        }

        if (classNode == null) {
            JOptionPane.showMessageDialog(frame, "Please select a class node to save.", "Save Class", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String defaultName = classNode.name.substring(classNode.name.lastIndexOf('/') + 1) + ".class";

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Class");
        chooser.setSelectedFile(new java.io.File(defaultName));
        int res = chooser.showSaveDialog(frame);
        if (res == JFileChooser.APPROVE_OPTION) {
            java.io.File file = chooser.getSelectedFile();

            try {
                controller.getProcessor().saveClassToFile(classNode, file);
                controller.log("Saved class to " + file.getAbsolutePath());
            } catch (Exception ex) {
                controller.log("Save class failed: " + ex.getMessage());
                JOptionPane.showMessageDialog(frame, "Save class failed: " + ex.getMessage(), "Save Class", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveSelectedJar() {
        String jarName = resolveSelectedJarName();
        if (jarName == null) {
            JOptionPane.showMessageDialog(frame, "Please select a JAR group or a class within it.", "Save Selected JAR", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Selected JAR");
        chooser.setSelectedFile(new java.io.File(jarName));

        int res = chooser.showSaveDialog(frame);
        if (res == JFileChooser.APPROVE_OPTION) {
            java.io.File file = chooser.getSelectedFile();

            if (!file.getName().toLowerCase().endsWith(".jar")) {
                file = new java.io.File(file.getParentFile(), file.getName() + ".jar");
            }

            try {
                controller.getProcessor().exportJarGroupToJar(jarName, file);
                controller.log("Saved JAR to " + file.getAbsolutePath());
            } catch (Exception ex) {
                controller.log("Save JAR failed: " + ex.getMessage());
                JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage(), "Save Selected JAR", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void overwriteOriginalJar() {
        String jarName = resolveSelectedJarName();
        if (jarName == null) {
            JOptionPane.showMessageDialog(frame, "Please select a JAR group or a class within it.", "Overwrite Original JAR", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        java.io.File original = controller.getProcessor().getOriginalJarFile(jarName);
        if (original == null) {
            JOptionPane.showMessageDialog(frame, "Original JAR path unknown for '" + jarName + "'. Try 'Export Selected JAR…' instead.", "Overwrite Original JAR", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            boolean ok = controller.getProcessor().overwriteOriginalJar(jarName);

            if (ok) {
                controller.log("Overwrote original JAR: " + original.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(frame, "Could not determine original JAR for '" + jarName + "'.", "Overwrite Original JAR", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            controller.log("Overwrite failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(frame, "Overwrite failed: " + ex.getMessage(), "Overwrite Original JAR", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String resolveSelectedJarName() {
        DefaultMutableTreeNode node = getTreePanel().getSelectedNode();

        switch (node) {
            case null -> {
                return null;
            }
            case JarMutableTreeNode jarMutableTreeNode -> {
                return jarMutableTreeNode.getJarName();
            }
            case ClassMutableTreeNode classMutableTreeNode -> {
                return classMutableTreeNode.getJarName();
            }
            case ClassNodeMutableTreeNode classNodeMutableTreeNode -> {
                return classNodeMutableTreeNode.getJarName();
            }
            default -> {
            }
        }

        // if user selected field/method, its parent is a ClassNodeMutableTreeNode under a ClassMutableTreeNode
        javax.swing.tree.TreeNode parent = node.getParent();
        if (parent instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            return classNodeMutableTreeNode.getJarName();
        }

        return null;
    }

    private void openSettingsDialog() {
        JDialog dialog = new JDialog(frame, "Settings", true);
        Settings initial = SettingsManager.getCurrent();
        SettingsPanel panel = new SettingsPanel(initial);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton apply = new JButton("Apply");
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(apply);
        buttons.add(ok);

        apply.addActionListener(e -> applySettings(panel));
        ok.addActionListener(e -> {
            applySettings(panel);
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel content = new JPanel(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void applySettings(SettingsPanel panel) {
        Settings s = panel.collectSettings();
        SettingsManager.applyAndSave(s, frame);
        // Refresh current views
        if (lastDisplayedClassNode != null) {
            contentPanelLeft.displayClass(lastDisplayedClassNode);
            if (asmJavaSplitEnabled && contentPanelRight != null) {
                contentPanelRight.displayBytecode(lastDisplayedClassNode);
            }
        }
    }
}
