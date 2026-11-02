# 0003: One thread changes a project; slow work returns results to it

**Status:** accepted

## Context

Loading a JAR froze the window for seconds, but moving work to other threads risked a rename changing class nodes
while another thread read them. ASM's tree nodes are mutable and not thread-safe.

## Decision

Exactly one thread changes a project: the Swing event thread in the GUI, the request thread in the MCP server. Slow
work runs on `TaskRunner`, one task at a time, and must not change the project: it returns a result that the event
thread applies. Work that needs class data on another thread first takes a snapshot on the owning thread, for
example `JarOperations.snapshot`, which converts classes to bytes before a JAR is written in the background.
The decompiler converts the class it is asked for to bytes before decompiling it on its own thread.

## Consequences

- No locks are needed around class nodes, and renames cannot race with exports.
- A result can be stale when it arrives; appliers check, for example that the same project is still open, or that
  no rename happened since an export started (`RenameOperations.changeCount`).
- Some work still runs on the event thread because it reads class nodes: planning and applying a mapping import
  pauses the window for about a second on a large JAR.
