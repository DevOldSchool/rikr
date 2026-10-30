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

/**
 * Shows the selected class as bytecode, optionally with its decompiled Java beside it. With no class open it shows
 * an empty state that says how to start.
 */
public class CodeView extends JPanel {
    private static final String EMPTY = "empty";
    private static final String CODE = "code";

    private final ContentPanel bytecodePanel;
    private final ContentPanel javaPanel;
    private final JPanel code = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private boolean javaShown;
    // Where the user last put the divider between bytecode and Java, as a share of the width.
    private double javaDivider = 0.5;
    private ClassNode displayedClass;
    private final Rikr controller;
    // Which class is open, and from which JAR, since the tree selection may be elsewhere.
    private final JLabel heading = new JLabel(" ");
    private final JPanel body = new JPanel(new BorderLayout());

    public CodeView(Rikr controller, EmptyStatePanel emptyState) {
        this.controller = controller;
        setLayout(cards);
        bytecodePanel = new ContentPanel(controller);
        javaPanel = new ContentPanel(controller, true);
        heading.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        heading.getAccessibleContext().setAccessibleName("Open class");
        code.add(heading, BorderLayout.NORTH);
        code.add(body, BorderLayout.CENTER);
        body.add(bytecodePanel, BorderLayout.CENTER);
        // With both views shown, a click in one shows the same method in the other.
        bytecodePanel.getContentPane().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                showInJava();
            }
        });
        javaPanel.getContentPane().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                showInBytecode();
            }
        });
        add(emptyState, EMPTY);
        add(code, CODE);
        cards.show(this, EMPTY);
        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    private void applyTheme() {
        heading.setOpaque(true);
        heading.setBackground(Theme.BACKGROUND_COLOR);
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
        heading.setText(String.format("<html><span style='color:#%02x%02x%02x'>%s  ›  </span><b>%s</b></html>",
            secondary.getRed(), secondary.getGreen(), secondary.getBlue(), escape(path),
            escape(name.substring(slash + 1))));
        heading.setToolTipText(jar + " · " + name);
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

    public void clear() {
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
        display(result.navigationClass());
        SwingUtilities.invokeLater(() -> bytecodePanel.selectTextAfterPattern(
            result.navigationPattern(),
            result.highlightText()
        ));
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

    private void showInJava() {
        if (!javaShown || displayedClass == null) {
            return;
        }
        JTextPane bytecode = bytecodePanel.getContentPane();
        JTextPane java = javaPanel.getContentPane();
        CodeLink.methodAtBytecode(displayedClass, bytecode.getText(), bytecode.getCaretPosition())
            .flatMap(method -> CodeLink.javaDeclaration(displayedClass, method, java.getText()))
            .ifPresent(range -> {
                java.select(range[0], range[1]);
                scrollToSelection(java);
            });
    }

    private void showInBytecode() {
        if (!javaShown || displayedClass == null) {
            return;
        }
        JTextPane java = javaPanel.getContentPane();
        CodeLink.methodAtJava(displayedClass, java.getText(), java.getCaretPosition())
            .ifPresent(method -> {
                bytecodePanel.selectTextAfterPattern(ClassNodeUtil.getMethodNodePattern(method),
                    method.name.startsWith("<") ? method.name.substring(1, method.name.length() - 1) : method.name);
                scrollToSelection(bytecodePanel.getContentPane());
            });
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
