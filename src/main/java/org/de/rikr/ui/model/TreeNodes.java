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
     * A bookmark for the class, field or method the node shows.
     */
    public static Optional<ProjectBookmark> bookmarkOf(DefaultMutableTreeNode node) {
        Optional<String> jar = jarOf(node);
        Optional<Declaration> declaration = declarationOf(node);
        if (jar.isEmpty() || declaration.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(switch (declaration.get()) {
            case Declaration.ClassDeclaration target ->
                new ProjectBookmark(ProjectBookmarkType.CLASS, jar.get(), target.owner().name, null, null);
            case Declaration.FieldDeclaration target -> new ProjectBookmark(ProjectBookmarkType.FIELD, jar.get(),
                target.owner().name, target.field().name, target.field().desc);
            case Declaration.MethodDeclaration target -> new ProjectBookmark(ProjectBookmarkType.METHOD, jar.get(),
                target.owner().name, target.method().name, target.method().desc);
        });
    }
}
