package org.de.rikr.symbol;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Textifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints the same text as {@link Textifier} and records where the printed lines name classes, fields and
 * methods. Each such line is stored as a {@link Line} in place of its string.
 */
class SymbolTextifier extends Textifier {
    // The class being printed, so member declarations know their owner.
    private String currentClass;

    SymbolTextifier() {
        super(Opcodes.ASM9);
    }

    /**
     * A printed line and the symbols it names, with offsets relative to the start of the line.
     */
    record Line(String text, List<SymbolSpan> spans) {
        @Override
        public String toString() {
            return text;
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        currentClass = name;
        int index = text.size();
        super.visit(version, access, name, signature, superName, interfaces);
        if (text.size() == index) {
            return;
        }
        LineMarker line = new LineMarker((String) text.get(index));
        // The declaration is the last line of the header, which ends with " {\n\n".
        String header = line.text;
        line.cursor = header.lastIndexOf('\n', header.length() - 3) + 1;
        String after = superName != null && !"java/lang/Object".equals(superName) ? " extends "
            : interfaces != null && interfaces.length > 0 ? " implements " : " {";
        line.markAt(header.indexOf(name + after, line.cursor), name, new Symbol.ClassSymbol(name));
        if (superName != null && !"java/lang/Object".equals(superName)) {
            line.markClass(superName);
        }
        if (interfaces != null) {
            for (String anInterface : interfaces) {
                line.markClass(anInterface);
            }
        }
        text.set(index, line.toLine());
    }

    @Override
    public Textifier visitField(int access, String name, String descriptor, String signature, Object value) {
        int index = text.size();
        Textifier result = super.visitField(access, name, descriptor, signature, value);
        LineMarker line = new LineMarker((String) text.get(index));
        line.cursor = lastLineStart(line.text);
        line.markDescriptor(descriptor);
        line.mark(" " + name, 1, new Symbol.FieldSymbol(currentClass, name, descriptor));
        text.set(index, line.toLine());
        return result;
    }

    @Override
    public Textifier visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        int index = text.size();
        Textifier result = super.visitMethod(access, name, descriptor, signature, exceptions);
        LineMarker line = new LineMarker((String) text.get(index));
        line.cursor = lastLineStart(line.text);
        // Search for the name and descriptor together: a short name such as "a" also appears in "private".
        line.markAt(line.text.indexOf(name + descriptor, line.cursor), name,
            new Symbol.MethodSymbol(currentClass, name, descriptor));
        line.markDescriptor(descriptor);
        if (exceptions != null) {
            for (String exception : exceptions) {
                line.markClass(exception);
            }
        }
        text.set(index, line.toLine());
        return result;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        int index = text.size();
        super.visitTypeInsn(opcode, type);
        LineMarker line = instructionLine(index, opcode);
        line.markClass(type);
        text.set(index, line.toLine());
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        int index = text.size();
        super.visitFieldInsn(opcode, owner, name, descriptor);
        LineMarker line = instructionLine(index, opcode);
        line.markClass(owner);
        line.mark("." + name, 1, new Symbol.FieldSymbol(owner, name, descriptor));
        line.markDescriptor(descriptor);
        text.set(index, line.toLine());
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        int index = text.size();
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        LineMarker line = instructionLine(index, opcode);
        line.markClass(owner);
        line.mark("." + name, 1, new Symbol.MethodSymbol(owner, name, descriptor));
        line.markDescriptor(descriptor);
        text.set(index, line.toLine());
    }

    @Override
    protected Textifier createTextifier() {
        SymbolTextifier child = new SymbolTextifier();
        child.currentClass = currentClass;
        return child;
    }

    private LineMarker instructionLine(int index, int opcode) {
        LineMarker line = new LineMarker((String) text.get(index));
        line.cursor = line.text.indexOf(OPCODES[opcode]) + OPCODES[opcode].length();
        return line;
    }

    private static int lastLineStart(String text) {
        return text.lastIndexOf('\n', text.length() - 2) + 1;
    }

    /**
     * Finds names in a line from left to right, so each name is looked for after the previous one.
     */
    private static final class LineMarker {
        private final String text;
        private final List<SymbolSpan> spans = new ArrayList<>();
        private int cursor;

        LineMarker(String text) {
            this.text = text;
        }

        /**
         * Mark the name that starts {@code skip} characters into the next occurrence of {@code pattern}.
         */
        void mark(String pattern, int skip, Symbol symbol) {
            int index = text.indexOf(pattern, cursor);
            if (index >= 0) {
                markAt(index + skip, pattern.substring(skip), symbol);
            }
        }

        void markAt(int index, String name, Symbol symbol) {
            if (index < cursor) {
                return;
            }
            spans.add(new SymbolSpan(index, index + name.length(), symbol));
            cursor = index + name.length();
        }

        void markClass(String internalName) {
            if (internalName.startsWith("[")) {
                markDescriptor(internalName);
                return;
            }
            markAt(text.indexOf(internalName, cursor), internalName, new Symbol.ClassSymbol(internalName));
        }

        /**
         * Mark every class named in a field or method descriptor, such as {@code a} and {@code b} in
         * {@code (La;I)[Lb;}.
         */
        void markDescriptor(String descriptor) {
            int start = text.indexOf(descriptor, cursor);
            if (start < 0) {
                return;
            }
            int position = 0;
            while ((position = descriptor.indexOf('L', position)) >= 0) {
                int end = descriptor.indexOf(';', position);
                if (end < 0) {
                    break;
                }
                String className = descriptor.substring(position + 1, end);
                spans.add(new SymbolSpan(start + position + 1, start + end, new Symbol.ClassSymbol(className)));
                position = end + 1;
            }
            cursor = start + descriptor.length();
        }

        Line toLine() {
            return new Line(text, spans);
        }
    }
}
