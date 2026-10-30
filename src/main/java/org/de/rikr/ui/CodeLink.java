package org.de.rikr.ui;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the same method in the bytecode and in the decompiled Java of a class, so a click in one view can show the
 * method in the other. Java is matched by name and parameter count, which is enough for most methods; when overloads
 * share both, the first is used.
 */
final class CodeLink {
    // A method or constructor declaration on one line: modifiers and type, a name, then parameters.
    private static final Pattern DECLARATION = Pattern.compile(
        "(?m)^[ \\t]*(?:[\\w$<>\\[\\],.? ]+[ \\t])?([\\w$]+)\\(([^()]*)\\)[ \\t]*(?:throws [\\w$., ]+)?[ \\t]*\\{");
    private static final Pattern STATIC_BLOCK = Pattern.compile("(?m)^[ \\t]*static[ \\t]*\\{");
    private static final Set<String> STATEMENTS = Set.of("if", "for", "while", "switch", "catch", "synchronized",
        "return", "new", "else", "do", "try");

    private CodeLink() {
    }

    /**
     * The method whose bytecode contains {@code offset}: the last method header at or before it.
     */
    static Optional<MethodNode> methodAtBytecode(ClassNode owner, String bytecode, int offset) {
        MethodNode found = null;
        int foundAt = -1;
        for (MethodNode method : owner.methods) {
            int at = bytecode.indexOf(method.name + method.desc);
            if (at >= 0 && at <= offset && at > foundAt) {
                found = method;
                foundAt = at;
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * The method whose Java declaration is the last one at or before {@code offset}.
     */
    static Optional<MethodNode> methodAtJava(ClassNode owner, String java, int offset) {
        String name = null;
        int parameters = 0;
        int foundAt = -1;
        Matcher declaration = DECLARATION.matcher(java);
        while (declaration.find() && declaration.start() <= offset) {
            if (STATEMENTS.contains(declaration.group(1))) {
                continue;
            }
            name = declaration.group(1);
            parameters = count(declaration.group(2));
            foundAt = declaration.start();
        }
        Matcher staticBlock = STATIC_BLOCK.matcher(java);
        while (staticBlock.find() && staticBlock.start() <= offset) {
            if (staticBlock.start() > foundAt) {
                name = "<clinit>";
                parameters = 0;
                foundAt = staticBlock.start();
            }
        }
        if (name == null) {
            return Optional.empty();
        }
        String bytecodeName = name.equals(simpleName(owner)) ? "<init>" : name;
        int count = parameters;
        return owner.methods.stream()
            .filter(method -> method.name.equals(bytecodeName))
            .filter(method -> Type.getArgumentTypes(method.desc).length == count)
            .findFirst();
    }

    /**
     * Where {@code method} is declared in the Java, as the start and end of its name, or empty if it is not found.
     */
    static Optional<int[]> javaDeclaration(ClassNode owner, MethodNode method, String java) {
        if (method.name.equals("<clinit>")) {
            Matcher staticBlock = STATIC_BLOCK.matcher(java);
            return staticBlock.find()
                ? Optional.of(new int[]{staticBlock.start(), staticBlock.end()})
                : Optional.empty();
        }
        String name = method.name.equals("<init>") ? simpleName(owner) : method.name;
        int parameters = Type.getArgumentTypes(method.desc).length;
        Matcher declaration = DECLARATION.matcher(java);
        while (declaration.find()) {
            if (declaration.group(1).equals(name) && count(declaration.group(2)) == parameters) {
                return Optional.of(new int[]{declaration.start(1), declaration.end(1)});
            }
        }
        return Optional.empty();
    }

    private static int count(String parameters) {
        return parameters.isBlank() ? 0 : parameters.split(",").length;
    }

    private static String simpleName(ClassNode owner) {
        String name = owner.name.substring(owner.name.lastIndexOf('/') + 1);
        return name.substring(name.lastIndexOf('$') + 1);
    }
}
