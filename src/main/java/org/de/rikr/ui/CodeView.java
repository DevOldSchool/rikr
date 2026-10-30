package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.settings.SettingsManager;
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

    public CodeView(Rikr controller, EmptyStatePanel emptyState) {
        setLayout(cards);
        bytecodePanel = new ContentPanel(controller);
        javaPanel = new ContentPanel(controller, true);
        code.add(bytecodePanel, BorderLayout.CENTER);
        add(emptyState, EMPTY);
        add(code, CODE);
        cards.show(this, EMPTY);
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
        code.removeAll();
        if (shown) {
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bytecodePanel, javaPanel);
            split.setResizeWeight(0.5);
            split.setDividerSize(5);
            // Without a location the split follows the panes' preferred widths, and long bytecode lines left the
            // Java pane a sliver at the right edge.
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
                if (split.getWidth() > 0 && split.isShowing()) {
                    javaDivider = Math.max(0.1, Math.min(0.9, (double) split.getDividerLocation() / split.getWidth()));
                }
            });
            code.add(split, BorderLayout.CENTER);
            double share = javaDivider;
            SwingUtilities.invokeLater(() -> split.setDividerLocation(share));
            SettingsManager.refreshThemeFor(javaPanel);
            SettingsManager.updateComponentTree(split);
            if (displayedClass != null) {
                javaPanel.displayBytecode(displayedClass);
            }
        } else {
            code.add(bytecodePanel, BorderLayout.CENTER);
        }
        code.revalidate();
        code.repaint();
    }

    public void display(ClassNode classNode) {
        displayedClass = classNode;
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

    private void select(String pattern, String name) {
        SwingUtilities.invokeLater(() -> bytecodePanel.selectTextAfterPattern(pattern, name));
    }
}
