package org.de.rikr.symbol;

/**
 * A class, field or method named in bytecode, identified by internal names and descriptors. A symbol is only
 * a reference: the class or member may be declared in a supertype, or outside the JAR.
 */
public sealed interface Symbol {

    record ClassSymbol(String className) implements Symbol {
    }

    record FieldSymbol(String owner, String name, String descriptor) implements Symbol {
    }

    record MethodSymbol(String owner, String name, String descriptor) implements Symbol {
    }
}
