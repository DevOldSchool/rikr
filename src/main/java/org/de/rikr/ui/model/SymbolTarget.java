package org.de.rikr.ui.model;

import org.de.rikr.symbol.Declaration;

/**
 * The class, field or method an action is about, chosen in the project tree or in a code view.
 */
public record SymbolTarget(String jar, Declaration declaration) {

    /**
     * The name it has now: a class's internal name, or a field's or method's simple name.
     */
    public String name() {
        return switch (declaration) {
            case Declaration.ClassDeclaration target -> target.owner().name;
            case Declaration.FieldDeclaration target -> target.field().name;
            case Declaration.MethodDeclaration target -> target.method().name;
        };
    }
}
