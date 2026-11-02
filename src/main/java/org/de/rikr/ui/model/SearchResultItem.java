package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;

public class SearchResultItem {
    private final String text;
    private final Icon icon;
    private final ClassNode classNode;
    private final String name;
    private final String pattern;
    private final int indentLevel;
    private final boolean selectable;

    public SearchResultItem(String text, Icon icon, ClassNode classNode, String name, String pattern) {
        this(text, icon, classNode, name, pattern, 0, true);
    }

    public SearchResultItem(String text, Icon icon, ClassNode classNode, String name, String pattern, int indentLevel) {
        this(text, icon, classNode, name, pattern, indentLevel, true);
    }

    public SearchResultItem(String text, Icon icon, ClassNode classNode, String name, String pattern, int indentLevel, boolean selectable) {
        this.text = text;
        this.icon = icon;
        this.classNode = classNode;
        this.name = name;
        this.pattern = pattern;
        this.indentLevel = indentLevel;
        this.selectable = selectable;
    }

    public String getText() {
        return text;
    }

    public Icon getIcon() {
        return icon;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
    }

    public int getIndentLevel() {
        return indentLevel;
    }

    public boolean isSelectable() {
        return selectable;
    }
}
