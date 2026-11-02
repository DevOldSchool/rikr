package org.de.rikr.symbol;

/**
 * Where a symbol is named in a text, from {@code start} inclusive to {@code end} exclusive.
 */
public record SymbolSpan(int start, int end, Symbol symbol) {
}
