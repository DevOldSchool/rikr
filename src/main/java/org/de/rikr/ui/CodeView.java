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
 * Shows the selected class as bytecode, optionally with its decompiled Java beside it.
 */
public class CodeView extends JPanel {
    private final ContentPanel bytecodePanel;
    private final ContentPanel javaPanel;
    private boolean javaShown;
    private ClassNode displayedClass;

    public CodeView(Rikr controller) {
        super(new BorderLayout());
        bytecodePanel = new ContentPanel(controller);
        javaPanel = new ContentPanel(controller, true);
        add(bytecodePanel, BorderLayout.CENTER);
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
        removeAll();
        if (shown) {
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bytecodePanel, javaPanel);
            split.setResizeWeight(0.5);
            split.setDividerSize(5);
            add(split, BorderLayout.CENTER);
            SettingsManager.refreshThemeFor(javaPanel);
            SettingsManager.updateComponentTree(split);
            if (displayedClass != null) {
                javaPanel.displayBytecode(displayedClass);
            }
        } else {
            add(bytecodePanel, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    public void display(ClassNode classNode) {
        displayedClass = classNode;
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

    private void select(String pattern, String name) {
        SwingUtilities.invokeLater(() -> bytecodePanel.selectTextAfterPattern(pattern, name));
    }
}
