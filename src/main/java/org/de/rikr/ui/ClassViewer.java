package org.de.rikr.ui;

import org.de.rikr.Logger;
import org.de.rikr.Rikr;
import org.de.rikr.ui.highlighter.LineHighlighter;
import org.de.rikr.ui.highlighter.SyntaxHighlighter;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassViewer implements Logger {
    private final JFrame frame;
    private final JTree tree;
    private final JTextPane detailsPane;
    private final JTextPane logPane;
    private final JScrollPane logScrollPane;
    private final JSplitPane verticalSplitPane;
    private final Rikr controller;

    private MenuBar menuBar = null;
    
    private StyledDocument logStyledDocument;
    private Style logStyle;
    private DateFormat dateFormat;
    private SyntaxHighlighter syntaxHighlighter;

    private Date date;

    public ClassViewer(Rikr controller) {
        this.controller = controller;
        frame = new JFrame("Rikr");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

        // Initialize menu bar
        menuBar = new MenuBar(
                e -> openFileDialog(),
                e -> toggleLogVisibility(menuBar.isLogVisible()),
                e -> openHierarchy()
        );
        frame.setJMenuBar(menuBar.getMenuBar());

        // Initialize split pane with tree and details pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(300);

        // Initialize tree
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

        JScrollPane treeScrollPane = new JScrollPane(tree);
        mainSplitPane.setLeftComponent(treeScrollPane);

        // Initialize details pane
        detailsPane = new JTextPane();
        detailsPane.setEditable(false);
        detailsPane.setBackground(new Color(31, 30, 34));
        detailsPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane detailsScrollPane = new JScrollPane(detailsPane);
        mainSplitPane.setRightComponent(detailsScrollPane);

        // Initialize log viewer
        logPane = new JTextPane();
        logPane.setEditable(false);
        logScrollPane = new JScrollPane(logPane);

        // Combine mainSplitPane and logScrollPane into a vertical split pane
        verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplitPane.setTopComponent(mainSplitPane);
        verticalSplitPane.setBottomComponent(logScrollPane);
        verticalSplitPane.setDividerLocation(600);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(verticalSplitPane, BorderLayout.CENTER);

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

                detailsPane.setStyledDocument(new DefaultStyledDocument());
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

        frame.add(panel);

        // Set up drop listener
        setDropTarget();
    }

    public void init() {
        logStyledDocument = logPane.getStyledDocument();
        logStyledDocument.setCharacterAttributes(0, logStyledDocument.getLength(), new SimpleAttributeSet(), true);
        logStyle = logStyledDocument.addStyle("log", null);
        dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        syntaxHighlighter = new SyntaxHighlighter();
        new LineHighlighter(detailsPane, new Color(43, 45, 48));

        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
        });
    }

    public void updateTree(Map<String, List<ClassNode>> jarClassesMap) {
        SwingUtilities.invokeLater(() -> {
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
            expandAllNodes(tree);
        });
    }

    public void displayClassDetails(ClassNode classNode) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument styledDocument = new DefaultStyledDocument();
            detailsPane.setStyledDocument(styledDocument);
            styledDocument.setCharacterAttributes(0, styledDocument.getLength(), new SimpleAttributeSet(), true);
            detailsPane.selectAll();

            // Get the classes bytecode in human-readable form
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            Textifier textifier = new Textifier();
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, textifier, printWriter);
            classNode.accept(traceClassVisitor);
            printWriter.flush();

            // Highlight and set caret position
            syntaxHighlighter.highlight(styledDocument, stringWriter.toString());
            detailsPane.setCaretPosition(0);
        });
    }

    private void openHierarchy() {
//        Map<ClassNode, List<ClassNode>> hierarchyMap = controller.getHierarchyMap();
//        HierarchyViewer hierarchyViewer = new HierarchyViewer(hierarchyMap);
//        hierarchyViewer.init();
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode.getParent() == null) {
            JOptionPane.showMessageDialog(frame, "Please select a class node to view its hierarchy", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            JOptionPane.showMessageDialog(frame, "Please select a jar file to view its class hierarchy", "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            HashMap<ClassNode, List<ClassNode>> hierarchyMap = controller.getHierarchyMap(selectedNode.toString());
            log(String.format("Displaying hierarchy for %d super classes and %d sub classes", hierarchyMap.size(), hierarchyMap.size()));
            new HierarchyViewer(hierarchyMap);
        }

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

    private void setDropTarget() {
        detailsPane.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = e.getTransferable();

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        Object transferData = transferable.getTransferData(DataFlavor.javaFileListFlavor);

                        if (transferData instanceof List<?> droppedFiles) {
                            for (Object item : droppedFiles) {
                                if (item instanceof File file) {
                                    if (file.getName().endsWith(".jar") || file.getName().endsWith(".class")) {
                                        processDroppedFile(file);
                                    }
                                }
                            }

                            e.dropComplete(true);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void processDroppedFile(File file) {
        if (file.getName().endsWith(".jar")) {
            controller.loadJarFiles(new File[]{file});
        } else if (file.getName().endsWith(".class")) {
            controller.loadClassFiles(new File[]{file});
        }
    }

    private void expandAllNodes(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void toggleLogVisibility(boolean isVisible) {
        if (isVisible) {
            verticalSplitPane.setBottomComponent(logScrollPane);
            verticalSplitPane.setDividerLocation(600); // Adjust this value based on your layout preference
        } else {
            verticalSplitPane.setBottomComponent(null);
        }
    }

    @Override
    public void log(String message) {
        System.out.println(message);

        SwingUtilities.invokeLater(() -> {
            try {
                date = new Date();
                StyleConstants.setForeground(logStyle, Color.GRAY);
                logStyledDocument.insertString(logStyledDocument.getLength(), dateFormat.format(date) + ": ", logStyle);
                StyleConstants.setForeground(logStyle, Color.LIGHT_GRAY);
                logStyledDocument.insertString(logStyledDocument.getLength(), message + "\n", logStyle);
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
            logPane.setCaretPosition(logStyledDocument.getLength());
        });
    }
}
