# 0004: Renames change nodes in place and are recorded as replayable steps

**Status:** accepted

## Context

Renaming by search-and-replace on names corrupted unrelated members with similar names, and lost overrides.
Views, bookmarks and original-name tracking all hold references to class, field and method nodes.

## Decision

Renames are computed with ASM's `Remapper` (`RenameEngine`), resolving each reference to the declaration it binds
to, renaming whole override families together. The remapped nodes are copied back into the original `ClassNode`,
`FieldNode` and `MethodNode` objects so they keep their identity. Every rename goes through `RenameService`, which
validates it and records a `RenameStep`: a single `RenameOperation`, or a `RenameBatch` from a mapping import whose
names are all as they were before the batch. The history is undone step by step and replayed when a project opens.

## Consequences

- Anything may key on node identity (`OriginalNames`, the flow views), and names can be recomputed after renames.
- Imports apply thousands of renames in one pass, and undo reverts the whole import.
- Overwriting a JAR removes the renames it contains from the history, since the file now holds them.
