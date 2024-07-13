package org.de.rikr.ui.model;

import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;

public class SearchResultItem {
    private final String text;
    private final Icon icon;
    private final ClassNode classNode;
    private final String name;
    private final String pattern;

    public SearchResultItem(String text, Icon icon, ClassNode classNode, String name, String pattern) {
        this.text = text;
        this.icon = icon;
        this.classNode = classNode;
        this.name = name;
        this.pattern = pattern;
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
}
