package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.decompiler.JetBrainsDecompilerService;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.handler.RenameActionHandler;
import org.de.rikr.ui.highlighter.LineHighlighter;
import org.de.rikr.ui.highlighter.SyntaxHighlighter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import javax.swing.*;
import javax.swing.text.*;
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
    private final SyntaxHighlighter javaSyntaxHighlighter;
    private final LineHighlighter lineHighlighter;
    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private Textifier textifier;
    private TraceClassVisitor traceClassVisitor;

    private final RenameActionHandler renameActionHandler;
    private final int[] originalCaretPosition = new int[1];
    private LineNumberComponent lineNumberComponent;

    private final JetBrainsDecompilerService decompilerService;
    private final boolean javaOnlyMode;

    public ContentPanel(Rikr controller) {
        this(controller, false);
    }

    public ContentPanel(Rikr controller, boolean javaOnlyMode) {
        this.controller = controller;
        this.decompilerService = new JetBrainsDecompilerService(controller::log);
        this.javaOnlyMode = javaOnlyMode;
        setBorder(null);

        contentPane = new ScrollableTextPane();
        contentPane.setEditable(false);
        contentPane.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        document = contentPane.getStyledDocument();
        style = document.addStyle("content", null);
        setViewportView(contentPane);

        syntaxHighlighter = new SyntaxHighlighter();
        javaSyntaxHighlighter = new SyntaxHighlighter();
        lineHighlighter = new LineHighlighter(contentPane, Theme.BACKGROUND_COLOR);
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

        // Add line number component
        lineNumberComponent = new LineNumberComponent(contentPane);
        setRowHeaderView(lineNumberComponent);

        // Apply colors now and on theme change
        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        contentPane.setBackground(Theme.CONTENT_BACKGROUND_COLOR);

        setCorner(JScrollPane.LOWER_RIGHT_CORNER, PanelUtil.createCorner());
        setCorner(JScrollPane.UPPER_RIGHT_CORNER, PanelUtil.createCorner());
        setCorner(JScrollPane.LOWER_LEFT_CORNER, PanelUtil.createCorner());
        setCorner(JScrollPane.UPPER_LEFT_CORNER, PanelUtil.createCorner());

        JScrollBar vsb = getVerticalScrollBar();
        if (vsb != null) {
            vsb.setBackground(SettingsManager.getScrollBarTrackColor());
        }

        JScrollBar hsb = getHorizontalScrollBar();
        if (hsb != null) {
            hsb.setBackground(SettingsManager.getScrollBarTrackColor());
        }

        if (lineHighlighter != null) {
            lineHighlighter.setColor(Theme.BACKGROUND_COLOR);
        }

        revalidate();
        repaint();
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
        // Keep for backward compatibility; delegates to displayClass
        displayClass(classNode);
    }

    public void displayClass(ClassNode classNode) {
        if (javaOnlyMode) {
            // Render decompiled Java in this panel
            SwingUtilities.invokeLater(() -> {
                clear();
                document.setCharacterAttributes(0, document.getLength(), style, true);
                contentPane.selectAll();

                // Try cache first
                String cached = controller.getProcessor() != null ? controller.getProcessor().getDecompiledSourceByClass(classNode.name) : null;
                if (cached != null && !cached.isEmpty()) {
                    javaSyntaxHighlighter.highlight(document, cached);
                    contentPane.setCaretPosition(0);
                } else {
                    contentPane.setText("// Decompiling with JetBrains engine...\n");
                    decompileInBackgroundJavaOnly(classNode);
                }
            });
        } else {
            // Render ASM
            SwingUtilities.invokeLater(() -> {
                clear();
                document.setCharacterAttributes(0, document.getLength(), style, true);
                contentPane.selectAll();

                // Get the class bytecode in human-readable form
                classNode.accept(traceClassVisitor);
                printWriter.flush();

                // Highlight and set caret position
                syntaxHighlighter.highlight(document, stringWriter.toString());
                contentPane.setCaretPosition(0);
            });
        }
    }

    private void decompileInBackgroundJavaOnly(ClassNode classNode) {
        // Try cache first (pre-decompiled on jar load)
        String cached = controller.getProcessor() != null ? controller.getProcessor().getDecompiledSourceByClass(classNode.name) : null;
        if (cached != null && !cached.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                javaSyntaxHighlighter.highlight(document, cached);
                contentPane.setCaretPosition(0);
            });
            return;
        }

        new Thread(() -> {
            String src = decompilerService.decompile(classNode);
            SwingUtilities.invokeLater(() -> {
                if (src == null || src.isEmpty()) {
                    contentPane.setText("// Failed to decompile using JetBrains decompiler.\n");
                } else {
                    javaSyntaxHighlighter.highlight(document, src);
                    contentPane.setCaretPosition(0);
                }
            });
        }, "rikr-decompiler-thread").start();
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

    public void selectTextAfterPattern(String pattern, String textToSelect) {
        Highlighter hilite = contentPane.getHighlighter();
        Document doc = contentPane.getDocument();
        String text = null;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        // Find the first occurrence of the pattern
        int patternPos = text.indexOf(pattern);
        if (patternPos >= 0) {
            int patternEndPos = patternPos + pattern.length();
            String patternText = text.substring(patternPos, patternEndPos);
            int selectPos = 0;

            // Search within the found pattern for textToSelect
            while ((selectPos = patternText.indexOf(textToSelect, selectPos)) >= 0) {
                // Ensure the textToSelect is not part of another word
                boolean isStandalone = true;
                if (selectPos > 0) {
                    char charBefore = patternText.charAt(selectPos - 1);
                    if (Character.isLetterOrDigit(charBefore)) {
                        isStandalone = false;
                    }
                }
                if (selectPos + textToSelect.length() < patternText.length()) {
                    char charAfter = patternText.charAt(selectPos + textToSelect.length());
                    if (Character.isLetterOrDigit(charAfter)) {
                        isStandalone = false;
                    }

                }

                if (isStandalone) {
                    int selectStart = patternPos + selectPos;
                    int selectEnd = selectStart + textToSelect.length();
                    try {
                        hilite.addHighlight(selectStart, selectEnd, new DefaultHighlighter.DefaultHighlightPainter(Theme.CONTENT_SELECTED_HIGHLIGHT_COLOR));
                    } catch (BadLocationException e) {
                        throw new RuntimeException(e);
                    }

                    // Set the caret position to the start of the textToSelect
                    contentPane.grabFocus();
                    contentPane.setCaretPosition(selectStart);
                    break;
                } else {
                    selectPos += textToSelect.length();
                }
            }
        }
    }

    public int getLineNumberOfPattern(String pattern) {
        Document doc = contentPane.getDocument();
        Element rootElement = doc.getDefaultRootElement();

        try {
            String content = doc.getText(0, doc.getLength());
            int offset = content.indexOf(pattern);

            if (offset != -1) {
                return rootElement.getElementIndex(offset);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public void setCaretPositionToLineNumber(int lineNumber) {
        Document doc = contentPane.getDocument();
        Element rootElement = doc.getDefaultRootElement();

        // Check if the line number is valid
        if (lineNumber < 0 || lineNumber >= rootElement.getElementCount()) {
            System.err.println("Unable to set caret position in ContentPanel, invalid line number");
            return;
        }

        // Get the start offset of the specified line
        Element lineElement = rootElement.getElement(lineNumber);
        int startOffset = lineElement.getStartOffset();

        // Set the caret position to the start offset of the line
        contentPane.setCaretPosition(startOffset);
    }
}
