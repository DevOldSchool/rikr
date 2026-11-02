package org.de.rikr.ui.model;

import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.symbol.Declaration;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Optional;

/**
 * What the project tree's nodes stand for: the JAR they belong to and the class, field or method they show.
 */
public final class TreeNodes {
    private TreeNodes() {
    }

    /**
     * The JAR of the node, found by walking up to its class or JAR node.
     */
    public static Optional<String> jarOf(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode current = node;
        while (current != null) {
            if (current instanceof JarMutableTreeNode jarNode) {
                return Optional.of(jarNode.getJarName());
            }
            if (current instanceof ClassMutableTreeNode classNode) {
                return Optional.of(classNode.getJarName());
            }
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return Optional.empty();
    }

    /**
     * The class, field or method the node shows. Empty for JAR and grouping nodes.
     */
    public static Optional<Declaration> declarationOf(DefaultMutableTreeNode node) {
        if (node == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (node) {
            case ClassMutableTreeNode classNode -> new Declaration.ClassDeclaration(classNode.getClassNode());
            case ClassNodeMutableTreeNode classNode -> new Declaration.ClassDeclaration(classNode.getClassNode());
            case InterfaceNodeMutableTreeNode classNode -> new Declaration.ClassDeclaration(classNode.getClassNode());
            case FieldNodeMutableTreeNode field -> new Declaration.FieldDeclaration(field.getOwner(), field.getFieldNode());
            case MethodNodeMutableTreeNode method -> new Declaration.MethodDeclaration(method.getOwner(), method.getMethodNode());
            case null, default -> null;
        });
    }

    /**
     * The class, field or method the node shows, with its JAR. Empty for JAR and grouping nodes.
     */
    public static Optional<SymbolTarget> targetOf(DefaultMutableTreeNode node) {
        Optional<String> jar = jarOf(node);
        Optional<Declaration> declaration = declarationOf(node);
        if (jar.isEmpty() || declaration.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SymbolTarget(jar.get(), declaration.get()));
    }

    /**
     * A bookmark for the class, field or method the node shows.
     */
    public static Optional<ProjectBookmark> bookmarkOf(DefaultMutableTreeNode node) {
        return targetOf(node).map(TreeNodes::bookmarkOf);
    }

    public static ProjectBookmark bookmarkOf(SymbolTarget target) {
        String jar = target.jar();
        return switch (target.declaration()) {
            case Declaration.ClassDeclaration value ->
                new ProjectBookmark(ProjectBookmarkType.CLASS, jar, value.owner().name, null, null);
            case Declaration.FieldDeclaration value -> new ProjectBookmark(ProjectBookmarkType.FIELD, jar,
                value.owner().name, value.field().name, value.field().desc);
            case Declaration.MethodDeclaration value -> new ProjectBookmark(ProjectBookmarkType.METHOD, jar,
                value.owner().name, value.method().name, value.method().desc);
        };
    }
}
