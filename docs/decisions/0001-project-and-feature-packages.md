# 0001: A project is one object, and code is grouped by feature

**Status:** accepted

## Context

Rikr began with a large `ClassProcessor` and a `service` package that every feature reached into. Each change to
renaming, mapping or bookmarks touched unrelated code, and nothing owned the state of a loaded project as a whole.

## Decision

`Project` owns everything loaded at once: the `Workspace` of classes, the `EventBus`, and one service per feature
(`JarOperations`, `RenameOperations`, `MappingOperations`, `Bookmarks`, references, decompiler, search). Opening a
project builds a new `Project` and replaces the old one whole. Code lives in a package per feature (`rename`,
`mapping`, `jar`, `flow` ...), with `ui` and `agent` as the two front ends over the same `Project`.

## Consequences

- The GUI (`Rikr`) and the MCP server (`agent/RikrTools`) share all behaviour; neither contains feature logic.
- A failed project open leaves the current project untouched.
- New features add a package and, usually, a service on `Project`, rather than growing an existing class.
