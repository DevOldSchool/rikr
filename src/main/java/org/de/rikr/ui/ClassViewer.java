package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
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
    private final ContentPanel contentPanel;
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
        contentPanel = new ContentPanel(controller);
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
        contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contentPanel, methodSimulatorPanel);
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
                e -> toggleLogVisibility(logPanel.isVisible())
        );
        frame.setJMenuBar(menuBar);

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
    }

    public void init() {
        contentPanel.init();
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

            frame.setVisible(true);
            searchBar.setVisible(false);

            contentPanel.getContentPane().grabFocus();
        });
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        SwingUtilities.invokeLater(() -> treePanel.updateTree(jarClassesMap));
    }

    public void displayBytecode(ClassNode classNode) {
        contentPanel.displayBytecode(classNode);
    }

    public void clearContent() {
        contentPanel.clear();
    }

    private void showProjectPanel() {
        ((JSplitPane) ((JSplitPane) verticalSplitPane.getTopComponent()).getLeftComponent()).setRightComponent(projectPanel);
        frame.revalidate();
        frame.repaint();
    }

    private void showSearchPanel() {
        ((JSplitPane) ((JSplitPane) verticalSplitPane.getTopComponent()).getLeftComponent()).setRightComponent(searchPanel);
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
        return contentPanel;
    }

    public JTextPane getContentPane() {
        return contentPanel.getContentPane();
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
