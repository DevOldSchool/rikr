package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Shows the selected class as bytecode, optionally with its decompiled Java beside it. With no class open it shows
 * an empty state that says how to start.
 */
public class CodeView extends JPanel {
    private static final String EMPTY = "empty";
    private static final String CODE = "code";
    private static final int LINK_DELAY_MS = 120;

    private final ContentPanel bytecodePanel;
    private final ContentPanel javaPanel;
    private final JPanel code = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private boolean javaShown;
    // Where the user last put the divider between bytecode and Java, as a share of the width.
    private double javaDivider = 0.5;
    private ClassNode displayedClass;
    private Object linkHighlight;
    private final Timer linkToJava = new Timer(LINK_DELAY_MS, event -> linkFrom(true));
    private final Timer linkToBytecode = new Timer(LINK_DELAY_MS, event -> linkFrom(false));
    // True while a link moves the other view's caret, which must not link back.
    private boolean linking;
    private final Rikr controller;
    // Which class is open, and from which JAR, since the tree selection may be elsewhere.
    private final JLabel heading = new JLabel(" ");
    // Bytecode alone, or with the decompiled Java beside it; the same choice as View › Split Bytecode and Java.
    private final JToggleButton bytecodeOnly = new JToggleButton("Bytecode", true);
    private final JToggleButton withJava = new JToggleButton("Bytecode and Java");
    // The method the person is working on, after the class, or null for the class alone.
    private MethodNode shownMethod;
    private final JPanel body = new JPanel(new BorderLayout());

    public CodeView(Rikr controller, EmptyStatePanel emptyState) {
        this.controller = controller;
        setLayout(cards);
        bytecodePanel = new ContentPanel(controller);
        javaPanel = new ContentPanel(controller, true);
        heading.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        heading.getAccessibleContext().setAccessibleName("Open class");
        ButtonGroup views = new ButtonGroup();
        views.add(bytecodeOnly);
        views.add(withJava);
        bytecodeOnly.setToolTipText("Show the bytecode alone");
        withJava.setToolTipText("Show the decompiled Java beside the bytecode (Shift+Cmd+J)");
        bytecodeOnly.addActionListener(event -> setJavaShown(false));
        withJava.addActionListener(event -> setJavaShown(true));
        // The chosen view takes the selection colours, so which one is on reads at a glance in every theme; the
        // look and feel's own selected toggle differs from an unselected one by a faint shade only.
        for (JToggleButton view : List.of(bytecodeOnly, withJava)) {
            view.putClientProperty("FlatLaf.style",
                "selectedBackground: $List.selectionBackground; selectedForeground: $List.selectionForeground");
        }
        JPanel switcher = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        switcher.setOpaque(false);
        switcher.getAccessibleContext().setAccessibleName("Code view");
        switcher.add(bytecodeOnly);
        switcher.add(withJava);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(heading, BorderLayout.CENTER);
        header.add(switcher, BorderLayout.EAST);
        code.add(header, BorderLayout.NORTH);
        code.add(body, BorderLayout.CENTER);
        body.add(bytecodePanel, BorderLayout.CENTER);
        // With both views shown, moving the caret in one, by clicking or with the keyboard, shows the same code in
        // the other. Moves are settled first, so holding an arrow key does not link every line it passes.
        linkToJava.setRepeats(false);
        linkToBytecode.setRepeats(false);
        bytecodePanel.getContentPane().addCaretListener(event -> caretMoved(linkToJava));
        javaPanel.getContentPane().addCaretListener(event -> caretMoved(linkToBytecode));
        add(emptyState, EMPTY);
        add(code, CODE);
        cards.show(this, EMPTY);
        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
        // The code's colours are fixed when it is highlighted, so a new theme, chosen in Settings or following the
        // system's appearance, needs the code highlighted again.
        SettingsManager.addListener(settings -> redisplay());
    }

    private void applyTheme() {
        heading.setOpaque(true);
        heading.setBackground(Theme.BACKGROUND_COLOR);
        if (heading.getParent() instanceof JComponent header) {
            header.setOpaque(true);
            header.setBackground(Theme.BACKGROUND_COLOR);
        }
        updateHeading();
    }

    private void updateHeading() {
        if (displayedClass == null) {
            heading.setText(" ");
            return;
        }
        String name = displayedClass.name;
        int slash = name.lastIndexOf('/');
        String jar = controller.jars().jarOf(displayedClass).orElse("");
        String path = jar + (slash < 0 ? "" : "  ›  " + name.substring(0, slash).replace('/', '.'));
        Color secondary = Theme.CONTENT_HIGHLIGHT_TEXT_COLOR;
        String colour = String.format("#%02x%02x%02x", secondary.getRed(), secondary.getGreen(), secondary.getBlue());
        String separator = "<span style='color:" + colour + "'>  ›  </span>";
        String simple = escape(name.substring(slash + 1));
        String crumbs = shownMethod == null ? "<b>" + simple + "</b>"
            : simple + separator + "<b>" + escape(MemberText.signature(shownMethod)) + "</b>";
        heading.setText("<html><span style='color:" + colour + "'>" + escape(path) + "</span>" + separator + crumbs
            + "</html>");
        heading.setToolTipText(jar + " · " + name + (shownMethod == null ? "" : "." + shownMethod.name
            + shownMethod.desc));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Each pane names itself when both are shown, so it is clear which side is which.
    private static JPanel labelled(ContentPanel pane, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        label.setForeground(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
        panel.add(label, BorderLayout.NORTH);
        panel.add(pane, BorderLayout.CENTER);
        return panel;
    }

    public void init() {
        bytecodePanel.init();
        javaPanel.init();
    }

    public ContentPanel bytecodePanel() {
        return bytecodePanel;
    }

    public boolean isJavaShown() {
        return javaShown;
    }

    public void setJavaShown(boolean shown) {
        if (shown == javaShown) {
            return;
        }
        javaShown = shown;
        bytecodeOnly.setSelected(!shown);
        withJava.setSelected(shown);
        firePropertyChange("javaShown", !shown, shown);
        body.removeAll();
        if (shown) {
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, labelled(bytecodePanel, "Bytecode"),
                labelled(javaPanel, "Java"));
            split.setResizeWeight(0.5);
            split.setDividerSize(5);
            // Without a location the split follows the panes' preferred widths, and long bytecode lines left the
            // Java pane a sliver at the right edge.
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
                if (split.getWidth() > 0 && split.isShowing()) {
                    javaDivider = Math.max(0.1, Math.min(0.9, (double) split.getDividerLocation() / split.getWidth()));
                }
            });
            body.add(split, BorderLayout.CENTER);
            double share = javaDivider;
            SwingUtilities.invokeLater(() -> split.setDividerLocation(share));
            SettingsManager.refreshThemeFor(javaPanel);
            SettingsManager.updateComponentTree(split);
            if (displayedClass != null) {
                javaPanel.displayBytecode(displayedClass);
            }
        } else {
            body.add(bytecodePanel, BorderLayout.CENTER);
        }
        body.revalidate();
        body.repaint();
    }

    public void display(ClassNode classNode) {
        if (classNode != displayedClass) {
            shownMethod = null;
        }
        displayedClass = classNode;
        updateHeading();
        cards.show(this, CODE);
        bytecodePanel.displayBytecode(classNode);
        if (javaShown) {
            javaPanel.displayBytecode(classNode);
        }
    }

    /**
     * Show the current class again, for example after the editor font changed.
     */
    public void redisplay() {
        if (displayedClass != null) {
            display(displayedClass);
        }
    }

    /**
     * Name the method the person chose in the heading, after its class; the class stays on screen.
     */
    public void showMethod(MethodNode method) {
        shownMethod = method;
        updateHeading();
    }

    /**
     * The method named in the heading, or null.
     */
    public MethodNode shownMethod() {
        return shownMethod;
    }

    public void clear() {
        shownMethod = null;
        displayedClass = null;
        bytecodePanel.clear();
        javaPanel.clear();
        cards.show(this, EMPTY);
    }

    /**
     * The class on screen, or null when none is.
     */
    public ClassNode displayedClass() {
        return displayedClass;
    }

    public void showReference(ReferenceResult result) {
        showReference(result, true);
    }

    /**
     * @param focus whether to move the keyboard focus to the code
     */
    public void showReference(ReferenceResult result, boolean focus) {
        if (displayedClass != result.navigationClass()) {
            display(result.navigationClass());
        }
        SwingUtilities.invokeLater(() -> {
            bytecodePanel.selectTextAfterPattern(result.navigationPattern(), result.highlightText(), focus);
            // Moves link only from the focused view, and stepping through results keeps the focus in their list, so
            // the Java is shown here once both views hold the class.
            bytecodePanel.whenShown(() -> javaPanel.whenShown(this::showInJava));
        });
    }

    /**
     * Show the bookmarked class and select the bookmarked member.
     *
     * @return false if the member no longer exists
     */
    public boolean showBookmark(ProjectBookmark bookmark, ClassNode owner) {
        display(owner);
        if (bookmark.type() == ProjectBookmarkType.CLASS) {
            select(ClassNodeUtil.getPattern(owner, null, null), owner.name);
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
            select(ClassNodeUtil.getFieldNodePattern(field), field.name);
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
        select(ClassNodeUtil.getMethodNodePattern(method), method.name);
        return true;
    }

    /**
     * Show the class and select a block of one of its methods, given the label that starts the block.
     */
    public void showBlock(ClassNode owner, MethodNode method, String label) {
        display(owner);
        String pattern = ClassNodeUtil.getMethodNodePattern(method);
        SwingUtilities.invokeLater(() -> bytecodePanel.selectInMethod(pattern, label));
    }

    // Only moves made in the focused view link; a view moved by a link, or scrolled by a search, does not.
    private void caretMoved(Timer link) {
        if (!linking && javaShown) {
            link.restart();
        }
    }

    // Focus is checked when the link runs, since a click moves the caret before the clicked view has focus.
    private void linkFrom(boolean bytecode) {
        if (bytecode && bytecodePanel.getContentPane().isFocusOwner()) {
            showInJava();
        } else if (!bytecode && javaPanel.getContentPane().isFocusOwner()) {
            showInBytecode();
        }
    }

    private void showInJava() {
        if (!javaShown || displayedClass == null) {
            return;
        }
        JTextPane bytecode = bytecodePanel.getContentPane();
        JTextPane java = javaPanel.getContentPane();
        CodeLink.bytecodeToJava(displayedClass, text(bytecode), bytecode.getCaretPosition(), text(java),
                controller.decompiler().getOriginalLines(displayedClass))
            .ifPresent(range -> showLinked(java, range));
    }

    private void showInBytecode() {
        if (!javaShown || displayedClass == null) {
            return;
        }
        JTextPane java = javaPanel.getContentPane();
        JTextPane bytecode = bytecodePanel.getContentPane();
        CodeLink.javaToBytecode(displayedClass, text(java), java.getCaretPosition(), text(bytecode),
                controller.decompiler().getOriginalLines(displayedClass))
            .ifPresent(range -> showLinked(bytecode, range));
    }

    // The document's own text, whose offsets match the caret's; getText() may change line endings.
    private static String text(JTextPane pane) {
        try {
            return pane.getDocument().getText(0, pane.getDocument().getLength());
        } catch (javax.swing.text.BadLocationException exception) {
            return "";
        }
    }

    // Marks the linked text with a highlight, which shows without taking focus from the pane that was clicked.
    private void showLinked(JTextPane pane, CodeLink.Range range) {
        javax.swing.text.Highlighter highlighter = pane.getHighlighter();
        if (linkHighlight != null) {
            bytecodePanel.getContentPane().getHighlighter().removeHighlight(linkHighlight);
            javaPanel.getContentPane().getHighlighter().removeHighlight(linkHighlight);
        }
        try {
            linkHighlight = highlighter.addHighlight(range.start(), range.end(),
                new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(
                    Theme.CONTENT_SELECTED_HIGHLIGHT_COLOR));
        } catch (javax.swing.text.BadLocationException ignored) {
            return;
        }
        linking = true;
        try {
            pane.setCaretPosition(range.start());
        } finally {
            linking = false;
        }
        scrollToSelection(pane);
    }

    // Scrolls to the selection, or to the caret when nothing is selected.
    private static void scrollToSelection(JTextPane pane) {
        try {
            // Make room below the line, so the method's start shows near the top rather than at the bottom edge.
            java.awt.geom.Rectangle2D start = pane.modelToView2D(pane.getSelectionStart());
            if (start != null) {
                Rectangle visible = start.getBounds();
                visible.height = Math.max(visible.height, pane.getVisibleRect().height * 2 / 3);
                pane.scrollRectToVisible(visible);
            }
        } catch (javax.swing.text.BadLocationException ignored) {
            // The selection came from this text.
        }
    }

    private void select(String pattern, String name) {
        SwingUtilities.invokeLater(() -> bytecodePanel.selectTextAfterPattern(pattern, name));
    }
}
