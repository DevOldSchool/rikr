package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.symbol.BytecodeText;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.symbol.Symbol;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.action.SymbolActions;
import org.de.rikr.ui.handler.RenameActionHandler;
import org.de.rikr.ui.highlighter.LineHighlighter;
import org.de.rikr.ui.highlighter.SyntaxHighlighter;
import org.de.rikr.ui.model.SymbolTarget;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContentPanel extends JScrollPane {
    private final Rikr controller;
    private final JTextPane contentPane;
    private final JPopupMenu contextMenu;
    private StyledDocument document;
    // Highlighting a large class takes seconds, so it is done off the event thread, into a document of its own
    // that replaces the one on screen in one step. Each display is numbered, so a late one is dropped.
    private final ExecutorService highlighting = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rikr-highlight");
        thread.setDaemon(true);
        return thread;
    });
    private long display;
    // Whether the last class asked for is on screen, and what waits for it to be.
    private boolean shown = true;
    private final List<Runnable> whenShown = new ArrayList<>();
    private final LineHighlighter lineHighlighter;
    private final RenameActionHandler renameActionHandler;
    // Where the context menu was opened, so Rename acts on the name that was clicked.
    private int popupOffset;
    private ClassNode displayedClass;
    // The symbols of the bytecode text on screen; null while Java is shown.
    private BytecodeText displayedBytecode;
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
        setViewportView(contentPane);

        lineHighlighter = new LineHighlighter(contentPane, Theme.BACKGROUND_COLOR);

        // Set up drop listener
        setDropTarget();

        // Context menu, filled each time it opens for the name it opens on
        contextMenu = new JPopupMenu();
        // The menu is shown by hand, not attached to a component, so a theme switch doesn't reach it on its own.
        SettingsManager.addListener(s -> SwingUtilities.updateComponentTreeUI(contextMenu));

        renameActionHandler = new RenameActionHandler(controller, this);

        // Disable default right-click behavior
        contentPane.setComponentPopupMenu(null);

        // Add line number component
        setRowHeaderView(new LineNumberComponent(contentPane));

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
        // The platform says which press opens a context menu: a right click, or a Control-click on a Mac.
        contentPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showMenuFor(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showMenuFor(e);
            }
        });
        // From the keyboard, the menu opens at the caret.
        String showMenu = "showContextMenu";
        contentPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("shift F10"), showMenu);
        contentPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("CONTEXT_MENU"), showMenu);
        contentPane.getActionMap().put(showMenu, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                int caret = contentPane.getCaretPosition();
                try {
                    Rectangle place = contentPane.modelToView2D(caret).getBounds();
                    showMenu(caret, place.x, place.y + place.height);
                } catch (BadLocationException ignored) {
                    showMenu(caret, 0, 0);
                }
            }
        });
    }

    private void showMenuFor(MouseEvent event) {
        if (event.isPopupTrigger()) {
            showMenu(contentPane.viewToModel2D(event.getPoint()), event.getX(), event.getY());
        }
    }

    // The actions that fit the name at the offset: the flow views for a method only.
    private void showMenu(int offset, int x, int y) {
        popupOffset = offset;
        SymbolActions actions = controller.getUserInterface().getSymbolActions();
        Optional<SymbolTarget> target = renameActionHandler.targetInCode(offset, false);
        contextMenu.removeAll();
        contextMenu.add(menuItem("Rename…", "shift F6", SymbolActions.Action.RENAME, offset));
        contextMenu.add(menuItem("Show References", "alt F7", SymbolActions.Action.SHOW_REFERENCES, offset));
        if (target.isPresent() && target.get().declaration() instanceof Declaration.MethodDeclaration) {
            contextMenu.addSeparator();
            contextMenu.add(menuItem("Show Control Flow", "alt F8", SymbolActions.Action.SHOW_CONTROL_FLOW, offset));
            contextMenu.add(menuItem("Show Call Graph", "alt F9", SymbolActions.Action.SHOW_CALL_GRAPH, offset));
            contextMenu.add(menuItem("Simulate Method", null, SymbolActions.Action.SIMULATE_METHOD, offset));
        }
        if (target.isPresent()) {
            contextMenu.addSeparator();
            contextMenu.add(menuItem(actions.isBookmarked(target.get()) ? "Remove Bookmark" : "Add Bookmark", "F3",
                SymbolActions.Action.TOGGLE_BOOKMARK, offset));
        }
        contextMenu.show(contentPane, x, y);
    }

    // The shortcut is shown as a reminder; the Code menu in the menu bar is what responds to it.
    private JMenuItem menuItem(String text, String shortcut, SymbolActions.Action action, int offset) {
        JMenuItem item = new JMenuItem(text);
        if (shortcut != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(shortcut));
        }
        item.addActionListener(event -> controller.getUserInterface().getSymbolActions().run(action, this, offset));
        return item;
    }

    public void displayBytecode(ClassNode classNode) {
        // Keep for backward compatibility; delegates to displayClass
        displayClass(classNode);
    }

    public void displayClass(ClassNode classNode) {
        clear();
        long request = display;
        shown = false;
        displayedClass = classNode;
        if (!javaOnlyMode) {
            BytecodeText bytecode = BytecodeText.of(classNode);
            show(request, bytecode.text(), bytecode);
            return;
        }
        String cached = controller.decompiler().getDecompiledSource(classNode);
        if (cached != null && !cached.isEmpty()) {
            show(request, cached, null);
            return;
        }
        contentPane.setText("// Decompiling with JetBrains engine...\n");
        controller.decompiler().decompileClassAsync(classNode).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            if (request != display) {
                return;
            }
            if (!result.success() || result.source().isEmpty()) {
                contentPane.setText("// " + result.message() + "\n");
                finishShowing();
            } else {
                show(request, result.source(), null);
            }
        }));
    }

    // Highlights the text off the event thread, then shows it unless another class was asked for meanwhile.
    private void show(long request, String text, BytecodeText bytecode) {
        StyledDocument highlighted = (StyledDocument) contentPane.getEditorKit().createDefaultDocument();
        highlighting.execute(() -> {
            new SyntaxHighlighter().highlight(highlighted, text);
            SwingUtilities.invokeLater(() -> {
                if (request != display) {
                    return;
                }
                setDocument(highlighted);
                displayedBytecode = bytecode;
                contentPane.setCaretPosition(0);
                finishShowing();
            });
        });
    }

    private void finishShowing() {
        shown = true;
        List<Runnable> waiting = List.copyOf(whenShown);
        whenShown.clear();
        waiting.forEach(Runnable::run);
    }

    /**
     * Run {@code action} once the class last asked for is on screen: now if it is, or when its text is ready. A class
     * is shown a moment after {@link #displayClass}, longer for a large one, and actions that find text in it, such
     * as selecting a method, must wait for it.
     */
    public void whenShown(Runnable action) {
        if (shown) {
            action.run();
        } else {
            whenShown.add(action);
        }
    }

    private void setDocument(StyledDocument replacement) {
        // Highlights hold positions in the old document, which mean nothing in the new one.
        contentPane.getHighlighter().removeAllHighlights();
        markedLine = null;
        targetHighlight = null;
        contentPane.setDocument(replacement);
        document = replacement;
    }

    /**
     * Empty the view and forget the class, including any that is still being shown.
     */
    public void clear() {
        clearMarkedLine();
        display++;
        whenShown.clear();
        shown = true;
        setDocument((StyledDocument) contentPane.getEditorKit().createDefaultDocument());
        displayedClass = null;
        displayedBytecode = null;
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
        whenShown(() -> contentPane.setCaretPosition(Math.min(offset, document.getLength())));
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
        whenShown(() -> selectInShownMethod(methodPattern, label));
    }

    private void selectInShownMethod(String methodPattern, String label) {
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

    // The mark selectTextAfterPattern last put on a name, removed when it marks another.
    private Object targetHighlight;

    public void selectTextAfterPattern(String pattern, String textToSelect) {
        selectTextAfterPattern(pattern, textToSelect, true);
    }

    /**
     * @param focus whether to move the keyboard focus to the code; false keeps it where it is, such as in a list
     *              of results being stepped through
     */
    public void selectTextAfterPattern(String pattern, String textToSelect, boolean focus) {
        whenShown(() -> selectShownTextAfterPattern(pattern, textToSelect, focus));
    }

    private void selectShownTextAfterPattern(String pattern, String textToSelect, boolean focus) {
        Highlighter hilite = contentPane.getHighlighter();
        Document doc = contentPane.getDocument();
        String text;
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
                        // One target at a time: stepping through results in the same class moves the mark.
                        if (targetHighlight != null) {
                            hilite.removeHighlight(targetHighlight);
                        }
                        targetHighlight = hilite.addHighlight(selectStart, selectEnd,
                            new DefaultHighlighter.DefaultHighlightPainter(Theme.CONTENT_SELECTED_HIGHLIGHT_COLOR));
                    } catch (BadLocationException e) {
                        throw new RuntimeException(e);
                    }

                    // Set the caret position to the start of the textToSelect
                    if (focus) {
                        contentPane.grabFocus();
                    }
                    contentPane.setCaretPosition(selectStart);
                    try {
                        // The caret only scrolls its view while it has the focus, so scroll explicitly.
                        contentPane.scrollRectToVisible(contentPane.modelToView2D(selectStart).getBounds());
                    } catch (BadLocationException ignored) {
                        // The caret is still placed.
                    }
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
