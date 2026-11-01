package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.project.ProjectLayout;
import org.de.rikr.mapping.SimilarClassReport;
import org.de.rikr.reference.ReferenceKind;
import org.de.rikr.resources.Images;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.action.ClassFileActions;
import org.de.rikr.ui.action.ProjectSessionActions;
import org.de.rikr.ui.action.SymbolActions;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

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
    private final NamingWorkspacePanel namingWorkspacePanel;
    private final FlowPanel flowPanel;
    private final MethodSimulatorPanel methodSimulatorPanel;
    private final JPanel rightPanel;
    private final SimulatorBar simulatorBar;
    private final ClassFileActions classFileActions;
    private final ProjectSessionActions projectSessionActions;
    private final EmptyStatePanel emptyState;
    private final StatusBar statusBar;
    private final NavigationHistory history = new NavigationHistory();
    private final SymbolActions symbolActions;
    // The simulator's width while open, kept when it closes so it reopens as it was; 0 until first shown.
    private int simulatorWidth;

    public ClassViewer(Rikr controller) {
        this.controller = controller;
        symbolActions = new SymbolActions(controller);
        frame = new JFrame("Rikr");
        List<Image> icons = Images.appIcons();
        frame.setIconImages(icons);
        // A packaged app takes its Dock icon from the bundle; set it here too for runs from the build.
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
            Taskbar.getTaskbar().setIconImage(SystemInfo.isMacOS ? Images.macAppIcon() : icons.getLast());
        }
        // Closing asks first when the project has unsaved work; see quit().
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
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
        classFileActions = new ClassFileActions(controller, frame, treePanel);
        projectSessionActions = new ProjectSessionActions(controller, frame);
        emptyState = new EmptyStatePanel(this::openFileDialog, projectSessionActions::openProject,
            projectSessionActions::open, controller::loadFiles);
        codeView = new CodeView(controller, emptyState);
        searchBar = new SearchBar(controller, e -> toggleSearchBarVisibility(false));
        logPanel = new LogPanel();
        projectPanel = new JPanel(new BorderLayout());
        projectPanel.add(treePanel, BorderLayout.CENTER);
        searchPanel = new SearchPanel(controller);
        mappingWorkspacePanel = new MappingWorkspacePanel(controller);
        referenceWorkspacePanel = new ReferenceWorkspacePanel(controller);
        bookmarkWorkspacePanel = new BookmarkWorkspacePanel(controller);
        namingWorkspacePanel = new NamingWorkspacePanel(controller);
        flowPanel = new FlowPanel(controller, this::openInBytecode);
        flowPanel.setViewListener(this::updateStatusContext);
        methodSimulatorPanel = new MethodSimulatorPanel(controller);
        simulatorBar = new SimulatorBar(controller, e -> {
            methodSimulatorPanel.stop();
            toggleMethodSimulatorVisibility(true);
        });

        // Create a panel to hold both the search panel and content panel
        rightPanel = new JPanel(new BorderLayout());

        rightPanel.add(searchBar, BorderLayout.NORTH);
        contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codeView, methodSimulatorPanel);
        contentSplitPane.setDividerLocation(700);
        // The code view takes any change in window width; the simulator keeps the width it was given.
        contentSplitPane.setResizeWeight(1.0);
        rightPanel.add(contentSplitPane, BorderLayout.CENTER);
        rightPanel.add(simulatorBar, BorderLayout.SOUTH);

        // Create the navigation panel
        navigationPanel = new NavigationPanel(
            e -> showWorkspace(projectPanel, rightPanel, projectPanel),
            e -> showWorkspace(searchPanel, rightPanel, searchPanel, searchColumnWidth()),
            e -> showMappingWorkspace(),
            e -> showWorkspace(projectPanel, referenceWorkspacePanel, referenceWorkspacePanel),
            e -> {
                bookmarkWorkspacePanel.refresh();
                showWorkspace(bookmarkWorkspacePanel, rightPanel, bookmarkWorkspacePanel);
            },
            e -> showWorkspace(projectPanel, flowPanel, flowPanel),
            e -> {
                showWorkspace(namingWorkspacePanel, rightPanel, namingWorkspacePanel);
                namingWorkspacePanel.refresh();
            }
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
        statusBar = new StatusBar(() -> toggleLogVisibility(logPanel.isVisible()));
        panel.add(statusBar, BorderLayout.SOUTH);
        // The decompiler's own messages and stack traces stay in the log; the status bar shows what Rikr itself
        // reports.
        logPanel.addMessageListener(message -> {
            if (!message.startsWith("[FernFlower]") && !message.startsWith("[Decompiler]")
                && !message.contains("\tat ")) {
                statusBar.setMessage(message);
            }
        });

        frame.add(panel);

        // Initialize menu bar
        menuBar = new MenuBar(controller, new MenuBar.Actions(
            this::openFileDialog,
            projectSessionActions::openProject,
            projectSessionActions::saveProject,
            projectSessionActions::saveProjectAs,
            classFileActions::saveSelectedClass,
            classFileActions::saveSelectedJar,
            classFileActions::overwriteOriginalJar,
            controller::undoLastRename,
            () -> toggleSearchBarVisibility(true),
            () -> findNext(1),
            () -> findNext(-1),
            this::goTo,
            () -> history.back(this::isLoaded).ifPresent(this::reveal),
            () -> history.forward(this::isLoaded).ifPresent(this::reveal),
            this::showProjectTab,
            this::showGlobalSearchTab,
            this::showMappingTab,
            this::showReferencesTab,
            this::showBookmarksTab,
            navigationPanel::selectControlFlowTab,
            navigationPanel::selectNamingTab,
            () -> toggleLogVisibility(logPanel.isVisible()),
            () -> SettingsDialog.open(frame),
            symbolActions::run
        ));
        frame.setJMenuBar(menuBar);

        // Put Preferences under the app menu, and let Quit ask about unsaved work as closing the window does.
        if (SystemInfo.isMacOS && java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            try {
                if (desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) {
                    desktop.setPreferencesHandler(e -> SettingsDialog.open(frame));
                }
                if (desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
                    desktop.setQuitHandler((event, response) -> {
                        if (projectSessionActions.confirmClose()) {
                            controller.shutdown();
                            response.performQuit();
                        } else {
                            response.cancelQuit();
                        }
                    });
                }
            } catch (UnsupportedOperationException ignored) {
            }
        }

        menuBar.getSplitAsmJavaItem().addActionListener(
            e -> codeView.setJavaShown(menuBar.getSplitAsmJavaItem().isSelected())
        );

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
        emptyState.update(false, RecentProjects.list());

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
            int classes = jarClassesMap.values().stream().mapToInt(List::size).sum();
            statusBar.setSummary(jarClassesMap.isEmpty() ? "No JARs loaded"
                : jarClassesMap.size() + (jarClassesMap.size() == 1 ? " JAR · " : " JARs · ")
                + NumberFormat.getIntegerInstance().format(classes) + " classes");
            emptyState.update(!jarClassesMap.isEmpty(), RecentProjects.list());
            flowPanel.refreshJars(List.copyOf(jarClassesMap.keySet()));
            treePanel.updateDirtyJars(controller.renames().dirtyJars());
            mappingWorkspacePanel.refreshJars(jarClassesMap);
            namingWorkspacePanel.refreshJars(jarClassesMap);
        });
    }

    /**
     * Say which dropped or chosen files could not be opened, in plain words; the reason is in the log.
     */
    public void showUnreadableFiles(List<File> files) {
        if (files.isEmpty()) {
            return;
        }
        String names = files.size() == 1 ? files.getFirst().getName() : files.size() + " files";
        controller.log("Couldn't open " + names + ".");
        Object[] options = {"OK", "Show Log"};
        int choice = JOptionPane.showOptionDialog(frame,
            "<html><b>Couldn't open " + names + "</b><br><br>"
                + (files.size() == 1 ? "It isn't" : "They aren't") + " a JAR or class file Rikr can read, or "
                + (files.size() == 1 ? "it's" : "they're") + " damaged. Choose a .jar or .class file.</html>",
            "Open", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 1 && !logPanel.isVisible()) {
            toggleLogVisibility(false);
        }
    }

    public void displayBytecode(ClassNode classNode) {
        history.visited(classNode);
        codeView.display(classNode);
        treePanel.follow(classNode);
        controller.jars().jarOf(classNode).ifPresent(jar -> referenceWorkspacePanel.classOpened(jar, classNode));
        bookmarkWorkspacePanel.classOpened(classNode);
        updateStatusContext();
    }

    /**
     * The person chose a method, in the tree, Go to or a graph. It becomes the current method: the heading and
     * status bar name it, and the References and Flow workspaces follow it when set to.
     */
    public void methodSelected(ClassNode owner, MethodNode method) {
        noteMethod(owner, method);
        controller.jars().jarOf(owner).ifPresent(jar -> flowPanel.methodOpened(jar, owner, method));
    }

    private void noteMethod(ClassNode owner, MethodNode method) {
        if (codeView.displayedClass() != owner) {
            displayBytecode(owner);
        }
        codeView.showMethod(method);
        controller.jars().jarOf(owner).ifPresent(jar -> referenceWorkspacePanel.methodOpened(jar, owner, method));
        updateStatusContext();
    }

    // The status bar's left slot says what is on screen; log lines borrow it for a few seconds.
    private void updateStatusContext() {
        ClassNode shown = codeView.displayedClass();
        MethodNode method = codeView.shownMethod();
        String subject = shown == null ? null
            : method == null ? shown.name.replace('/', '.') : MemberText.describe(shown, method);
        String context = switch (navigationPanel.getSelectedTabId()) {
            case "SEARCH" -> "Search";
            case "MAPPING" -> "Cross-JAR mappings";
            case "BOOKMARKS" -> "Bookmarks";
            case "REFERENCES" -> subject == null ? "References" : "References · " + subject;
            case "CONTROL_FLOW" -> flowPanel.describeView() == null ? "Entry points and flow"
                : flowPanel.describeView();
            default -> subject == null ? "" : "Bytecode · " + subject;
        };
        statusBar.setContext(context);
    }

    /**
     * Show that background work is running, or has finished.
     */
    public void setBusy(boolean busy) {
        statusBar.setBusy(busy);
    }

    private boolean isLoaded(ClassNode classNode) {
        return controller.jars().jarOf(classNode).isPresent();
    }

    /**
     * Select the class in the project tree, which opens it; open it directly if the tree does not show it.
     */
    private void reveal(ClassNode classNode) {
        navigationPanel.selectProjectTab();
        if (!treePanel.reveal(classNode)) {
            displayBytecode(classNode);
        }
    }

    private void goTo() {
        List<GoToDialog.Target> recent = history.recent(10).stream()
            .flatMap(classNode -> controller.jars().jarOf(classNode).stream()
                .map(jar -> new GoToDialog.Target(jar, classNode, null, null)))
            .toList();
        GoToDialog.open(frame, controller.jars().classesByJar(), recent, target -> {
            reveal(target.owner());
            if (target.field() != null) {
                SwingUtilities.invokeLater(() -> getContentPanel().selectTextAfterPattern(
                    ClassNodeUtil.getFieldNodePattern(target.field()), target.field().name));
            } else if (target.method() != null) {
                methodSelected(target.owner(), target.method());
                SwingUtilities.invokeLater(() -> getContentPanel().selectTextAfterPattern(
                    ClassNodeUtil.getMethodNodePattern(target.method()), target.method().name));
            }
        });
    }

    private void findNext(int direction) {
        if (!searchBar.isVisible()) {
            toggleSearchBarVisibility(true);
            return;
        }
        searchBar.navigate(direction);
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

    /**
     * Show classes a search found, such as subclasses, in the References workspace.
     */
    public void showClasses(String description, String jarName, List<ClassNode> classes, ReferenceKind kind,
                            String relation) {
        referenceWorkspacePanel.showClasses(description, jarName, classes, kind, relation);
        navigationPanel.selectReferencesTab();
    }

    public void showSimilarClasses(String jarName, ClassNode source, SimilarClassReport report) {
        referenceWorkspacePanel.showSimilarClasses(jarName, source, report);
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
            noteMethod(owner, method);
            navigationPanel.selectControlFlowTab();
            flowPanel.showControlFlow(jar, owner, method);
        });
    }

    /**
     * Draw the calls the method makes and switch to them.
     */
    public void showCallGraph(ClassNode owner, MethodNode method) {
        controller.jars().jarOf(owner).ifPresent(jar -> {
            noteMethod(owner, method);
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
        history.clear();
        codeView.clear();
        flowPanel.clear();
    }

    /**
     * Show a workspace: {@code left} beside the navigation bar and {@code right} in the main area.
     *
     * @param opened the workspace's own panel, whose theme may be out of date while it was hidden
     */
    private void showWorkspace(Component left, Component right, JComponent opened) {
        showWorkspace(left, right, opened, 300);
    }

    /**
     * @param dividerLocation how wide the navigation bar and {@code left} are together
     */
    private void showWorkspace(Component left, Component right, JComponent opened, int dividerLocation) {
        leftSplitPane.setRightComponent(left);
        mainSplitPane.setRightComponent(right);
        mainSplitPane.setDividerLocation(dividerLocation);
        SettingsManager.refreshThemeFor(opened);
        frame.revalidate();
        frame.repaint();
        if (opened == flowPanel) {
            flowPanel.showPending();
        }
        updateStatusContext();
    }

    // Mapping compares two classes side by side and never uses the tree, so it gets the whole width.
    private void showMappingWorkspace() {
        mappingWorkspacePanel.refreshJars(controller.jars().classesByJar());
        showWorkspace(null, mappingWorkspacePanel, mappingWorkspacePanel,
            navigationPanel.getPreferredSize().width + leftSplitPane.getDividerSize());
    }

    // Search rows carry an opcode, owner and member, so they need more room than tree rows.
    private int searchColumnWidth() {
        return Math.max(300, Math.min(600, frame.getWidth() * 2 / 5));
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
            if (methodSimulatorPanel.getWidth() > 0) {
                simulatorWidth = methodSimulatorPanel.getWidth();
            }
            contentSplitPane.setRightComponent(null);
            contentSplitPane.setDividerSize(0);
            methodSimulatorPanel.setVisible(false);
            toggleSimulatorBarVisibility(false);
        } else {
            contentSplitPane.setRightComponent(methodSimulatorPanel);
            contentSplitPane.setDividerSize(1);
            contentSplitPane.setDividerLocation(Math.max(0, contentSplitPane.getWidth() - simulatorWidth()
                - contentSplitPane.getDividerSize()));
            methodSimulatorPanel.setVisible(true);
            toggleSimulatorBarVisibility(true);
        }
    }

    // The width the simulator opens at: as it was last left, or else a third of the space with room for
    // its values, while leaving the code view at least half.
    private int simulatorWidth() {
        int available = contentSplitPane.getWidth();
        int width = simulatorWidth > 0 ? simulatorWidth : Math.max(380, available / 3);
        return Math.min(width, available / 2);
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

    /**
     * Count the named classes again after a rename, when the Naming workspace is on screen.
     */
    public void refreshNaming() {
        if (namingWorkspacePanel.isShowing()) {
            namingWorkspacePanel.refresh();
        }
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

    /**
     * Mark the window as holding unsaved work: on macOS the close button shows a dot.
     */
    public void setEdited(boolean edited) {
        frame.getRootPane().putClientProperty("Window.documentModified", edited);
    }

    private void quit() {
        if (!projectSessionActions.confirmClose()) {
            return;
        }
        controller.shutdown();
        frame.dispose();
        System.exit(0);
    }

    public SymbolActions getSymbolActions() {
        return symbolActions;
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
