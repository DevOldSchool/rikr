package org.de.rikr.symbol;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A class, field or method declared in a loaded JAR.
 */
public sealed interface Declaration {

    ClassNode owner();

    record ClassDeclaration(ClassNode owner) implements Declaration {
    }

    record FieldDeclaration(ClassNode owner, FieldNode field) implements Declaration {
    }

    record MethodDeclaration(ClassNode owner, MethodNode method) implements Declaration {
    }
}
