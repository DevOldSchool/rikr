package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.project.ProjectLayout;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.action.ClassFileActions;
import org.de.rikr.ui.action.ProjectSessionActions;
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
    private final CodeView codeView;
    private final LogPanel logPanel;
    private final JPanel projectPanel;
    private final SearchPanel searchPanel;
    private final MappingWorkspacePanel mappingWorkspacePanel;
    private final ReferenceWorkspacePanel referenceWorkspacePanel;
    private final BookmarkWorkspacePanel bookmarkWorkspacePanel;
    private final FlowPanel flowPanel;
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
        codeView = new CodeView(controller);
        searchBar = new SearchBar(controller, e -> toggleSearchBarVisibility(false));
        logPanel = new LogPanel();
        projectPanel = new JPanel(new BorderLayout());
        projectPanel.add(treePanel, BorderLayout.CENTER);
        searchPanel = new SearchPanel(controller);
        mappingWorkspacePanel = new MappingWorkspacePanel(controller);
        referenceWorkspacePanel = new ReferenceWorkspacePanel(controller);
        bookmarkWorkspacePanel = new BookmarkWorkspacePanel(controller);
        flowPanel = new FlowPanel(controller, this::openInBytecode);
        methodSimulatorPanel = new MethodSimulatorPanel(controller);
        simulatorBar = new SimulatorBar(controller, e -> toggleMethodSimulatorVisibility(methodSimulatorPanel.isVisible()));
        classFileActions = new ClassFileActions(controller, frame, treePanel);
        projectSessionActions = new ProjectSessionActions(controller, frame);

        // Create a panel to hold both the search panel and content panel
        rightPanel = new JPanel(new BorderLayout());

        rightPanel.add(searchBar, BorderLayout.NORTH);
        contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codeView, methodSimulatorPanel);
        contentSplitPane.setDividerLocation(700);
        contentSplitPane.setResizeWeight(0.80);
        rightPanel.add(contentSplitPane, BorderLayout.CENTER);
        rightPanel.add(simulatorBar, BorderLayout.SOUTH);

        // Create the navigation panel
        navigationPanel = new NavigationPanel(
            controller,
            e -> showWorkspace(projectPanel, rightPanel, projectPanel),
            e -> showWorkspace(searchPanel, rightPanel, searchPanel),
            e -> {
                mappingWorkspacePanel.refreshJars(controller.jars().classesByJar());
                showWorkspace(projectPanel, mappingWorkspacePanel, mappingWorkspacePanel);
            },
            e -> showWorkspace(projectPanel, referenceWorkspacePanel, referenceWorkspacePanel),
            e -> {
                bookmarkWorkspacePanel.refresh();
                showWorkspace(bookmarkWorkspacePanel, rightPanel, bookmarkWorkspacePanel);
            },
            e -> showWorkspace(projectPanel, flowPanel, flowPanel)
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
            e -> SettingsDialog.open(frame, codeView::redisplay)
        );
        frame.setJMenuBar(menuBar);

        // Put Preferences under the app menu
        if (SystemInfo.isMacOS && java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            try {
                if (desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) {
                    desktop.setPreferencesHandler(e -> SettingsDialog.open(frame, codeView::redisplay));
                }
            } catch (UnsupportedOperationException ignored) {
            }
        }

        menuBar.getSplitAsmJavaItem().addActionListener(
            e -> codeView.setJavaShown(menuBar.getSplitAsmJavaItem().isSelected())
        );

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
        codeView.init();
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

            codeView.bytecodePanel().getContentPane().grabFocus();
        });
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        SwingUtilities.invokeLater(() -> {
            treePanel.updateTree(jarClassesMap);
            flowPanel.refreshJars(List.copyOf(jarClassesMap.keySet()));
            treePanel.updateDirtyJars(controller.renames().dirtyJars());
            mappingWorkspacePanel.refreshJars(jarClassesMap);
        });
    }

    public void displayBytecode(ClassNode classNode) {
        codeView.display(classNode);
    }

    /**
     * Show the class on screen again, for example after renames it did not start.
     */
    public void refreshCode() {
        codeView.redisplay();
    }

    public void showClassReferences(String jarName, ClassNode classNode) {
        referenceWorkspacePanel.showClassReferences(jarName, classNode);
        navigationPanel.selectReferencesTab();
    }

    public void showFieldReferences(String jarName, ClassNode owner, FieldNode field) {
        referenceWorkspacePanel.showFieldReferences(jarName, owner, field);
        navigationPanel.selectReferencesTab();
    }

    private void openInBytecode(ClassNode owner, MethodNode method, String label) {
        navigationPanel.selectProjectTab();
        codeView.showBlock(owner, method, label);
    }

    /**
     * Draw the method's control flow graph and switch to it.
     */
    public void showControlFlow(ClassNode owner, MethodNode method) {
        controller.jars().jarOf(owner).ifPresent(jar -> {
            navigationPanel.selectControlFlowTab();
            flowPanel.showControlFlow(jar, owner, method);
        });
    }

    /**
     * Draw the calls the method makes and switch to them.
     */
    public void showCallGraph(ClassNode owner, MethodNode method) {
        controller.jars().jarOf(owner).ifPresent(jar -> {
            navigationPanel.selectControlFlowTab();
            flowPanel.showCallGraph(jar, owner, method);
        });
    }

    /**
     * Switch to the flow tab with the JAR's entry points to choose from.
     */
    public void showEntryPoints(String jar) {
        navigationPanel.selectControlFlowTab();
        flowPanel.showEntryPoints(jar);
    }

    public void showMethodReferences(String jarName, ClassNode owner, MethodNode method) {
        referenceWorkspacePanel.showMethodReferences(jarName, owner, method);
        navigationPanel.selectReferencesTab();
    }

    public void openReferenceResult(ReferenceResult result) {
        navigationPanel.selectProjectTab();
        codeView.showReference(result);
    }

    public boolean openBookmark(ProjectBookmark bookmark, ClassNode owner) {
        return codeView.showBookmark(bookmark, owner);
    }

    public void clearContent() {
        codeView.clear();
        flowPanel.clear();
    }

    /**
     * Show a workspace: {@code left} beside the navigation bar and {@code right} in the main area.
     *
     * @param opened the workspace's own panel, whose theme may be out of date while it was hidden
     */
    private void showWorkspace(Component left, Component right, JComponent opened) {
        leftSplitPane.setRightComponent(left);
        mainSplitPane.setRightComponent(right);
        mainSplitPane.setDividerLocation(300);
        SettingsManager.refreshThemeFor(opened);
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

    /**
     * Redraw the control flow graph, whose instructions show names that a rename may have changed.
     */
    public void refreshControlFlow() {
        SwingUtilities.invokeLater(flowPanel::refresh);
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
            codeView.isJavaShown(),
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
            if (codeView.isJavaShown() != layout.splitJavaVisible()) {
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
        return codeView.bytecodePanel();
    }

    public JTextPane getContentPane() {
        return codeView.bytecodePanel().getContentPane();
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
}
