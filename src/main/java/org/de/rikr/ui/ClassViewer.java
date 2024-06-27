package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
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
import java.util.List;
import java.util.Map;

public class ClassViewer {
    private final JFrame frame;
    private final JTree tree;
    private final JTextPane detailsPane;
    private final Rikr controller;

    public ClassViewer(Rikr controller) {
        this.controller = controller;
        frame = new JFrame("Rikr");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

        // Initialize menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openMenuItem = new JMenuItem("Open");
        openMenuItem.addActionListener(e -> openFileDialog());
        fileMenu.add(openMenuItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // Initialize split pane with tree and details pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);

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
        splitPane.setLeftComponent(treeScrollPane);

        // Initialize details pane
        detailsPane = new JTextPane();
        detailsPane.setEditable(false);
        detailsPane.setBackground(new Color(31, 30, 34));
        detailsPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane detailsScrollPane = new JScrollPane(detailsPane);
        splitPane.setRightComponent(detailsScrollPane);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

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

            try {
                styledDocument.insertString(0, stringWriter.toString(), new SimpleAttributeSet());
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
            detailsPane.setCaretPosition(0);
        });
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
}
