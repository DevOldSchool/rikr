package org.de.rikr.service;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public record SearchResult(
    String text,
    SearchResultIcon icon,
    ClassNode classNode,
    FieldNode fieldNode,
    MethodNode methodNode,
    String name,
    String pattern,
    int indentLevel,
    boolean selectable
) {
    public static SearchResult classResult(ClassNode classNode, String name, String pattern, boolean selectable) {
        return new SearchResult(name, SearchResultIcon.CLASS, classNode, null, null, name, pattern, 0, selectable);
    }

    public static SearchResult interfaceResult(ClassNode classNode, String text, String name, String pattern) {
        return new SearchResult(text, SearchResultIcon.INTERFACE, classNode, null, null, name, pattern, 1, true);
    }

    public static SearchResult fieldResult(
        ClassNode classNode,
        FieldNode fieldNode,
        String text,
        String name,
        String pattern
    ) {
        return new SearchResult(text, SearchResultIcon.FIELD, classNode, fieldNode, null, name, pattern, 1, true);
    }

    public static SearchResult methodResult(
        ClassNode classNode,
        MethodNode methodNode,
        String text,
        String name,
        String pattern
    ) {
        return new SearchResult(text, SearchResultIcon.METHOD, classNode, null, methodNode, name, pattern, 1, true);
    }

    public static SearchResult instructionResult(ClassNode classNode, String text, String name, String pattern) {
        return new SearchResult(text, SearchResultIcon.INSTRUCTION, classNode, null, null, name, pattern, 2, true);
    }
}
