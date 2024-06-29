package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.handler.RenameActionHandler;
import org.de.rikr.ui.highlighter.LineHighlighter;
import org.de.rikr.ui.highlighter.SyntaxHighlighter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyledDocument;
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

public class ContentPanel extends JScrollPane {
    private final Rikr controller;
    private final JTextPane contentPane;
    private final JPopupMenu contextMenu;
    private final JMenuItem renameItem;
    private final StyledDocument document;
    private final Style style;
    private final SyntaxHighlighter syntaxHighlighter;
    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private Textifier textifier;
    private TraceClassVisitor traceClassVisitor;

    private final RenameActionHandler renameActionHandler;
    private final int[] originalCaretPosition = new int[1];

    public ContentPanel(Rikr controller) {
        this.controller = controller;

        contentPane = new JTextPane();
        contentPane.setEditable(false);
        contentPane.setBackground(new Color(31, 30, 34));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        document = contentPane.getStyledDocument();
        style = document.addStyle("content", null);
        setViewportView(contentPane);

        syntaxHighlighter = new SyntaxHighlighter();
        new LineHighlighter(contentPane, new Color(43, 45, 48));
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        textifier = new Textifier();
        traceClassVisitor = new TraceClassVisitor(null, textifier, printWriter);

        // Set up drop listener
        setDropTarget();

        // Context menu for renaming
        contextMenu = new JPopupMenu();
        renameItem = new JMenuItem("Rename");
        contextMenu.add(renameItem);

        renameActionHandler = new RenameActionHandler(controller, contentPane, document, originalCaretPosition);

        // Disable default right-click behavior
        contentPane.setComponentPopupMenu(null);
    }

    public void init() {
        renameItem.addActionListener(renameActionHandler);

        // Initialize original caret position listener
        contentPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    originalCaretPosition[0] = contentPane.getCaretPosition();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    contextMenu.show(contentPane, e.getX(), e.getY());
                }
            }
        });
    }

    public void displayBytecode(ClassNode classNode) {
        SwingUtilities.invokeLater(() -> {
            clear();
            document.setCharacterAttributes(0, document.getLength(), style, true);
            contentPane.selectAll();

            // Get the classes bytecode in human-readable form
            classNode.accept(traceClassVisitor);
            printWriter.flush();

            // Highlight and set caret position
            syntaxHighlighter.highlight(document, stringWriter.toString());
            contentPane.setCaretPosition(0);
        });
    }

    public void clear() {
        try {
            document.remove(0, document.getLength());
            stringWriter = new StringWriter();
            printWriter = new PrintWriter(stringWriter);
            textifier = new Textifier();
            traceClassVisitor = new TraceClassVisitor(null, textifier, printWriter);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setDropTarget() {
        contentPane.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = e.getTransferable();

                    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        return;
                    }

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

    public JTextPane getContentPane() {
        return contentPane;
    }

    public RenameActionHandler getRenameActionHandler() {
        return renameActionHandler;
    }
}
