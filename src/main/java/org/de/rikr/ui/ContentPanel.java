package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.symbol.BytecodeText;
import org.de.rikr.symbol.Symbol;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.handler.RenameActionHandler;
import org.de.rikr.ui.highlighter.LineHighlighter;
import org.de.rikr.ui.highlighter.SyntaxHighlighter;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.Optional;

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
    private final RenameActionHandler renameActionHandler;
    // Where the context menu was opened, so Rename acts on the name that was clicked.
    private int popupOffset;
    private ClassNode displayedClass;
    // The symbols of the bytecode text on screen; null while Java is shown.
    private BytecodeText displayedBytecode;
    private LineNumberComponent lineNumberComponent;
    // The simulator's current instruction, painted across the whole line; null when nothing is marked.
    private Object markedLine;

    private final boolean javaOnlyMode;

    public ContentPanel(Rikr controller) {
        this(controller, false);
    }

    public ContentPanel(Rikr controller, boolean javaOnlyMode) {
        this.controller = controller;
        this.javaOnlyMode = javaOnlyMode;
        setBorder(null);

        contentPane = new ScrollableTextPane();
        contentPane.setEditable(false);
        contentPane.getAccessibleContext().setAccessibleName(javaOnlyMode ? "Java" : "Bytecode");
        contentPane.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        document = contentPane.getStyledDocument();
        style = document.addStyle("content", null);
        setViewportView(contentPane);

        syntaxHighlighter = new SyntaxHighlighter();
        javaSyntaxHighlighter = new SyntaxHighlighter();
        lineHighlighter = new LineHighlighter(contentPane, Theme.BACKGROUND_COLOR);

        // Set up drop listener
        setDropTarget();

        // Context menu for renaming
        contextMenu = new JPopupMenu();
        renameItem = new JMenuItem("Rename");
        contextMenu.add(renameItem);
        // The menu is shown by hand, not attached to a component, so a theme switch doesn't reach it on its own.
        SettingsManager.addListener(s -> SwingUtilities.updateComponentTreeUI(contextMenu));

        renameActionHandler = new RenameActionHandler(controller, this);

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
                    popupOffset = contentPane.viewToModel2D(e.getPoint());
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
                displayedClass = classNode;
                document.setCharacterAttributes(0, document.getLength(), style, true);
                contentPane.selectAll();

                // Try cache first
                String cached = controller.decompiler().getDecompiledSource(classNode);
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

                BytecodeText bytecode = BytecodeText.of(classNode);
                syntaxHighlighter.highlight(document, bytecode.text());
                displayedClass = classNode;
                displayedBytecode = bytecode;
                contentPane.setCaretPosition(0);
            });
        }
    }

    private void decompileInBackgroundJavaOnly(ClassNode classNode) {
        // Try cache first (pre-decompiled on jar load)
        String cached = controller.decompiler().getDecompiledSource(classNode);
        if (cached != null && !cached.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                javaSyntaxHighlighter.highlight(document, cached);
                contentPane.setCaretPosition(0);
            });
            return;
        }

        controller.decompiler().decompileClassAsync(classNode).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                if (!result.success() || result.source().isEmpty()) {
                    contentPane.setText("// " + result.message() + "\n");
                } else {
                    javaSyntaxHighlighter.highlight(document, result.source());
                    contentPane.setCaretPosition(0);
                }
            });
        });
    }

    public void clear() {
        try {
            clearMarkedLine();
            document.remove(0, document.getLength());
            displayedClass = null;
            displayedBytecode = null;
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isJavaOnly() {
        return javaOnlyMode;
    }

    /**
     * The class shown in this panel, or null when it is empty.
     */
    public ClassNode getDisplayedClass() {
        return displayedClass;
    }

    public int getPopupOffset() {
        return popupOffset;
    }

    /**
     * The class, field or method named at the offset of the bytecode text. Always empty while Java is shown.
     */
    public Optional<Symbol> symbolAt(int offset) {
        return displayedBytecode == null ? Optional.empty() : displayedBytecode.symbolAt(offset);
    }

    /**
     * Move the caret back to an offset after the class is shown again, for example after a rename.
     */
    public void restoreCaret(int offset) {
        SwingUtilities.invokeLater(() -> contentPane.setCaretPosition(Math.min(offset, document.getLength())));
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
                    controller.log("Failed to process dropped file: " + ex.getMessage());
                }
            }
        });
    }

    private void processDroppedFile(File file) {
        controller.loadFiles(List.of(file));
    }

    public JTextPane getContentPane() {
        return contentPane;
    }

    public RenameActionHandler getRenameActionHandler() {
        return renameActionHandler;
    }

    /**
     * Select the label that starts a block of a method, as the bytecode view prints it, or the method's
     * declaration when {@code label} is null or not found.
     *
     * @param methodPattern the method's name and descriptor, which only its declaration prints together
     */
    public void selectInMethod(String methodPattern, String label) {
        String text;
        try {
            text = document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            return;
        }
        int method = text.indexOf(methodPattern);
        if (method < 0) {
            return;
        }
        int start = method;
        int end = method + methodPattern.length();
        if (label != null) {
            // Textifier prints a label on a line of its own, indented by three spaces.
            int at = text.indexOf("\n   " + label + "\n", method);
            if (at >= 0) {
                start = at + 4;
                end = start + label.length();
            }
        }
        contentPane.grabFocus();
        contentPane.select(start, end);
        try {
            // Show the block's first instructions below the label, not just the label at the edge of the view.
            Rectangle shown = contentPane.modelToView2D(start).getBounds();
            shown.height += getViewport().getHeight() / 2;
            contentPane.scrollRectToVisible(shown);
        } catch (BadLocationException ignored) {
            // The selection is still made.
        }
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
            controller.log("Unable to resolve line for pattern: " + e.getMessage());
        }

        return -1;
    }

    /**
     * Mark a line across the full width of the view and scroll it into view, replacing any line marked before.
     * The caret and focus stay where they are, so the keyboard keeps working in whatever has it.
     */
    public void markLine(int lineNumber) {
        clearMarkedLine();
        Element root = document.getDefaultRootElement();
        if (lineNumber < 0 || lineNumber >= root.getElementCount()) {
            return;
        }
        Element line = root.getElement(lineNumber);
        try {
            markedLine = contentPane.getHighlighter().addHighlight(line.getStartOffset(), line.getStartOffset(),
                (g, p0, p1, bounds, component) -> {
                    try {
                        Rectangle row = component.modelToView2D(p0).getBounds();
                        g.setColor(Theme.CONTENT_SELECTED_HIGHLIGHT_COLOR);
                        g.fillRect(0, row.y, component.getWidth(), row.height);
                    } catch (BadLocationException ignored) {
                        // The line went away; nothing to paint.
                    }
                });
            Rectangle row = contentPane.modelToView2D(line.getStartOffset()).getBounds();
            Rectangle view = getViewport().getViewRect();
            // Scroll only when the line nears an edge, then put it a third of the way down, so the
            // instructions that follow are in view and stepping doesn't make the text jump every time.
            int margin = row.height * 3;
            if (row.y < view.y + margin || row.y + row.height > view.y + view.height - margin) {
                int top = Math.max(0, row.y - view.height / 3);
                int bottom = Math.max(0, contentPane.getHeight() - view.height);
                getViewport().setViewPosition(new java.awt.Point(view.x, Math.min(top, bottom)));
            }
        } catch (BadLocationException ignored) {
            // The line is in range, so this can't happen.
        }
        contentPane.repaint();
    }

    public void clearMarkedLine() {
        if (markedLine != null) {
            contentPane.getHighlighter().removeHighlight(markedLine);
            markedLine = null;
            contentPane.repaint();
        }
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
