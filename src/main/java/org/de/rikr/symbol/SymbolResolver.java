package org.de.rikr.symbol;

import org.de.rikr.rename.ClassHierarchy;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds where a symbol is declared in one JAR.
 */
public final class SymbolResolver {
    private SymbolResolver() {
    }

    /**
     * The declaration of the symbol in the JAR's classes. A field or method referenced through a subclass is
     * found in the supertype that declares it. Empty when the class or member is not declared in the JAR, for
     * example {@code java/lang/Object.toString}.
     */
    public static Optional<Declaration> resolve(List<ClassNode> classes, Symbol symbol) {
        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        return switch (symbol) {
            case Symbol.ClassSymbol classSymbol -> Optional.ofNullable(hierarchy.get(classSymbol.className()))
                .map(Declaration.ClassDeclaration::new);
            case Symbol.FieldSymbol field -> findField(hierarchy, field);
            case Symbol.MethodSymbol method -> findMethod(hierarchy, method);
        };
    }

    /**
     * The class itself and the fields and methods it declares whose name is {@code name}. The class matches by
     * its name without the package.
     */
    public static List<Declaration> declaredByName(ClassNode classNode, String name) {
        List<Declaration> matches = new ArrayList<>();
        if (classNode.name.substring(classNode.name.lastIndexOf('/') + 1).equals(name)) {
            matches.add(new Declaration.ClassDeclaration(classNode));
        }
        for (FieldNode field : classNode.fields) {
            if (field.name.equals(name)) {
                matches.add(new Declaration.FieldDeclaration(classNode, field));
            }
        }
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name)) {
                matches.add(new Declaration.MethodDeclaration(classNode, method));
            }
        }
        return matches;
    }

    private static Optional<Declaration> findField(ClassHierarchy hierarchy, Symbol.FieldSymbol symbol) {
        for (String className : hierarchy.selfAndAncestors(symbol.owner())) {
            ClassNode owner = hierarchy.get(className);
            if (owner == null) {
                continue;
            }
            for (FieldNode field : owner.fields) {
                if (field.name.equals(symbol.name()) && field.desc.equals(symbol.descriptor())) {
                    return Optional.of(new Declaration.FieldDeclaration(owner, field));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Declaration> findMethod(ClassHierarchy hierarchy, Symbol.MethodSymbol symbol) {
        for (String className : hierarchy.selfAndAncestors(symbol.owner())) {
            ClassNode owner = hierarchy.get(className);
            if (owner == null) {
                continue;
            }
            for (MethodNode method : owner.methods) {
                if (method.name.equals(symbol.name()) && method.desc.equals(symbol.descriptor())) {
                    return Optional.of(new Declaration.MethodDeclaration(owner, method));
                }
            }
        }
        return Optional.empty();
    }
}
