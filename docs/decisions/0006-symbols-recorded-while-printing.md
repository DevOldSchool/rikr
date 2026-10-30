# 0006: The bytecode view records symbols while printing

**Status:** accepted

## Context

Renaming from the code view guessed the target from the text of the selected line, so it picked the wrong member
when names repeated, and obfuscated names such as `a` appear inside keywords.

## Decision

The bytecode view prints classes with `SymbolTextifier`, which produces the same text as ASM's `Textifier` and
records the span of every class, field and method name it prints (`BytecodeText`). A click maps to a `Symbol`,
which `SymbolResolver` resolves to its declaration. The Java view, which has no such record, only renames a name
that the class on screen declares exactly once.

## Consequences

- Renames from the bytecode view act on exactly the name clicked.
- Control flow graphs name blocks with the same label names as the bytecode view, since both come from
  `Textifier`'s naming.
