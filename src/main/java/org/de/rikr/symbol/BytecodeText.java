package org.de.rikr.symbol;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A class printed as ASM bytecode text, with the position of every class, field and method it names.
 */
public record BytecodeText(String text, List<SymbolSpan> spans) {
    public BytecodeText {
        spans = List.copyOf(spans);
    }

    public static BytecodeText of(ClassNode classNode) {
        SymbolTextifier textifier = new SymbolTextifier();
        classNode.accept(new TraceClassVisitor(null, textifier, null));
        StringBuilder text = new StringBuilder();
        List<SymbolSpan> spans = new ArrayList<>();
        flatten(textifier.getText(), text, spans);
        return new BytecodeText(text.toString(), spans);
    }

    /**
     * The symbol named at the offset. An offset just after a name also counts, because clicking the right
     * half of a name's last character puts the caret there.
     */
    public Optional<Symbol> symbolAt(int offset) {
        SymbolSpan touching = null;
        for (SymbolSpan span : spans) {
            if (span.start() <= offset && offset < span.end()) {
                return Optional.of(span.symbol());
            }
            if (offset == span.end()) {
                touching = span;
            }
        }
        return Optional.ofNullable(touching).map(SymbolSpan::symbol);
    }

    private static void flatten(List<?> parts, StringBuilder text, List<SymbolSpan> spans) {
        for (Object part : parts) {
            if (part instanceof List<?> nested) {
                flatten(nested, text, spans);
            } else if (part instanceof SymbolTextifier.Line line) {
                int offset = text.length();
                for (SymbolSpan span : line.spans()) {
                    spans.add(new SymbolSpan(offset + span.start(), offset + span.end(), span.symbol()));
                }
                text.append(line.text());
            } else {
                text.append(part);
            }
        }
    }
}
