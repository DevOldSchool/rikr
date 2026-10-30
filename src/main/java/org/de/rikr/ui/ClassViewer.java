package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.project.ProjectLayout;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.settings.Settings;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.action.ClassFileActions;
import org.de.rikr.ui.action.ProjectSessionActions;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ClassViewer {
    private final JFrame frame;
    private final Rikr controller;
    private final MenuBar menuBar;
    private final JSplitPane mainSplitPane;
    private final JSplitPane leftSplitPane;
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
    private final MappingWorkspacePanel mappingWorkspacePanel;
    private final ReferenceWorkspacePanel referenceWorkspacePanel;
    private final BookmarkWorkspacePanel bookmarkWorkspacePanel;
    private final MethodSimulatorPanel methodSimulatorPanel;
    private final JPanel rightPanel;
    private final SimulatorBar simulatorBar;
    private final ClassFileActions classFileActions;
    private final ProjectSessionActions projectSessionActions;

    public ClassViewer(Rikr controller) {
        this.controller = controller;
        frame = new JFrame("Rikr");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.shutdown();
            }
        });

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
        mappingWorkspacePanel = new MappingWorkspacePanel(controller);
        referenceWorkspacePanel = new ReferenceWorkspacePanel(controller);
        bookmarkWorkspacePanel = new BookmarkWorkspacePanel(controller);
        methodSimulatorPanel = new MethodSimulatorPanel(controller);
        simulatorBar = new SimulatorBar(controller, e -> toggleMethodSimulatorVisibility(methodSimulatorPanel.isVisible()));
        classFileActions = new ClassFileActions(controller, frame, treePanel);
        projectSessionActions = new ProjectSessionActions(controller, frame);

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
            e -> showSearchPanel(),
            e -> showMappingPanel(),
            e -> showReferencePanel(),
            e -> showBookmarkPanel()
        );

        // Create a split pane for the navigation panel and tree panel
        leftSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
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
            e -> projectSessionActions.openProject(),
            e -> projectSessionActions.saveProject(),
            e -> projectSessionActions.saveProjectAs(),
            e -> toggleSearchBarVisibility(false),
            e -> controller.undoLastRename(),
            e -> showProjectTab(),
            e -> showGlobalSearchTab(),
            e -> showMappingTab(),
            e -> showReferencesTab(),
            e -> showBookmarksTab(),
            e -> toggleLogVisibility(logPanel.isVisible()),
            e -> classFileActions.saveSelectedClass(),
            e -> classFileActions.saveSelectedJar(),
            e -> classFileActions.overwriteOriginalJar(),
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
        mappingWorkspacePanel.init();
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
        SwingUtilities.invokeLater(() -> {
            treePanel.updateTree(jarClassesMap);
            treePanel.updateDirtyJars(controller.renames().dirtyJars());
            mappingWorkspacePanel.refreshJars(jarClassesMap);
        });
    }

    public void displayBytecode(ClassNode classNode) {
        lastDisplayedClassNode = classNode;

        contentPanelLeft.displayBytecode(classNode);
        if (asmJavaSplitEnabled) {
            contentPanelRight.displayBytecode(classNode);
        }
    }

    public void showClassReferences(String jarName, ClassNode classNode) {
        referenceWorkspacePanel.showClassReferences(jarName, classNode);
        navigationPanel.selectReferencesTab();
    }

    public void showFieldReferences(String jarName, ClassNode owner, FieldNode field) {
        referenceWorkspacePanel.showFieldReferences(jarName, owner, field);
        navigationPanel.selectReferencesTab();
    }

    public void showMethodReferences(String jarName, ClassNode owner, MethodNode method) {
        referenceWorkspacePanel.showMethodReferences(jarName, owner, method);
        navigationPanel.selectReferencesTab();
    }

    public void openReferenceResult(ReferenceResult result) {
        navigationPanel.selectProjectTab();
        displayBytecode(result.navigationClass());
        SwingUtilities.invokeLater(() -> contentPanelLeft.selectTextAfterPattern(
            result.navigationPattern(),
            result.highlightText()
        ));
    }

    public boolean openBookmark(ProjectBookmark bookmark, ClassNode owner) {
        displayBytecode(owner);
        if (bookmark.type() == ProjectBookmarkType.CLASS) {
            SwingUtilities.invokeLater(() -> contentPanelLeft.selectTextAfterPattern(
                ClassNodeUtil.getPattern(owner, null, null),
                owner.name
            ));
            return true;
        }
        if (bookmark.type() == ProjectBookmarkType.FIELD) {
            FieldNode field = owner.fields.stream()
                .filter(candidate -> candidate.name.equals(bookmark.memberName()))
                .filter(candidate -> candidate.desc.equals(bookmark.descriptor()))
                .findFirst()
                .orElse(null);
            if (field == null) {
                return false;
            }
            SwingUtilities.invokeLater(() -> contentPanelLeft.selectTextAfterPattern(
                ClassNodeUtil.getFieldNodePattern(field),
                field.name
            ));
            return true;
        }
        MethodNode method = owner.methods.stream()
            .filter(candidate -> candidate.name.equals(bookmark.memberName()))
            .filter(candidate -> candidate.desc.equals(bookmark.descriptor()))
            .findFirst()
            .orElse(null);
        if (method == null) {
            return false;
        }
        SwingUtilities.invokeLater(() -> contentPanelLeft.selectTextAfterPattern(
            ClassNodeUtil.getMethodNodePattern(method),
            method.name
        ));
        return true;
    }

    public void clearContent() {
        lastDisplayedClassNode = null;
        contentPanelLeft.clear();
        contentPanelRight.clear();
    }

    private void showProjectPanel() {
        leftSplitPane.setRightComponent(projectPanel);
        mainSplitPane.setRightComponent(rightPanel);
        mainSplitPane.setDividerLocation(300);
        SettingsManager.refreshThemeFor(projectPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showSearchPanel() {
        leftSplitPane.setRightComponent(searchPanel);
        mainSplitPane.setRightComponent(rightPanel);
        mainSplitPane.setDividerLocation(300);
        SettingsManager.refreshThemeFor(searchPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showMappingPanel() {
        leftSplitPane.setRightComponent(projectPanel);
        mainSplitPane.setRightComponent(mappingWorkspacePanel);
        mainSplitPane.setDividerLocation(300);
        mappingWorkspacePanel.refreshJars(controller.jars().classesByJar());
        SettingsManager.refreshThemeFor(mappingWorkspacePanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showReferencePanel() {
        leftSplitPane.setRightComponent(projectPanel);
        mainSplitPane.setRightComponent(referenceWorkspacePanel);
        mainSplitPane.setDividerLocation(300);
        SettingsManager.refreshThemeFor(referenceWorkspacePanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showBookmarkPanel() {
        leftSplitPane.setRightComponent(bookmarkWorkspacePanel);
        mainSplitPane.setRightComponent(rightPanel);
        mainSplitPane.setDividerLocation(300);
        bookmarkWorkspacePanel.refresh();
        SettingsManager.refreshThemeFor(bookmarkWorkspacePanel);
        frame.revalidate();
        frame.repaint();
    }

    private void openFileDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(frame);

        if (result == JFileChooser.APPROVE_OPTION) {
            controller.loadFiles(List.of(fileChooser.getSelectedFiles()));
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

    private void showMappingTab() {
        navigationPanel.selectMappingTab();
    }

    private void showReferencesTab() {
        navigationPanel.selectReferencesTab();
    }

    private void showBookmarksTab() {
        navigationPanel.selectBookmarksTab();
    }

    public void refreshBookmarks() {
        SwingUtilities.invokeLater(bookmarkWorkspacePanel::refresh);
    }

    public void refreshTreeLabels() {
        SwingUtilities.invokeLater(treePanel::refreshLabels);
    }

    public void updateDirtyJars(java.util.Set<String> dirtyJars) {
        SwingUtilities.invokeLater(() -> treePanel.updateDirtyJars(dirtyJars));
    }

    public ProjectLayout captureProjectLayout() {
        Rectangle bounds = frame.getBounds();
        return new ProjectLayout(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            mainSplitPane.getDividerLocation(),
            verticalSplitPane.getDividerLocation(),
            contentSplitPane.getDividerLocation(),
            logPanel.isVisible(),
            methodSimulatorPanel.isVisible(),
            asmJavaSplitEnabled,
            navigationPanel.getSelectedTabId()
        );
    }

    public void applyProjectLayout(ProjectLayout layout) {
        SwingUtilities.invokeLater(() -> {
            if (layout.windowWidth() > 0 && layout.windowHeight() > 0) {
                frame.setBounds(
                    layout.windowX(),
                    layout.windowY(),
                    layout.windowWidth(),
                    layout.windowHeight()
                );
            }
            if (logPanel.isVisible() != layout.logVisible()) {
                toggleLogVisibility(logPanel.isVisible());
            }
            if (methodSimulatorPanel.isVisible() != layout.simulatorVisible()) {
                toggleMethodSimulatorVisibility(methodSimulatorPanel.isVisible());
            }
            if (asmJavaSplitEnabled != layout.splitJavaVisible()) {
                menuBar.getSplitAsmJavaItem().doClick();
            }
            navigationPanel.selectTab(layout.selectedWorkspace());
            mainSplitPane.setDividerLocation(layout.mainDivider());
            verticalSplitPane.setDividerLocation(layout.verticalDivider());
            contentSplitPane.setDividerLocation(layout.contentDivider());
        });
    }

    public void updateProjectTitle(File projectFile) {
        frame.setTitle(projectFile == null ? "Rikr" : "Rikr - " + projectFile.getName());
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
