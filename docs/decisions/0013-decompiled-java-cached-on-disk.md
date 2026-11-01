# 0013: Decompiled Java is kept on disk by class bytes

**Status:** accepted

## Context

The largest classes of game clients take 8 to 12 seconds to decompile even in parallel (decision 0012), and Rikr
decompiled them again in every run: after a restart, after reopening a project, and in the GUI and the MCP server
separately. `DecompilerCacheService` kept sources only in memory, per class node, for as long as the class wasn't
renamed.

## Decision

`SourceCache` keeps decompiled Java on disk, in `~/.rikr/cache/decompiled` or the folder chosen in Settings. The
folder and the size limit are saved with the other settings (`DecompilerCacheSettings`); the GUI applies them to
each project and whenever they change, and the MCP server reads them from the same file, so both share one cache.
The `rikr.cache.dir` system property wins over both, which keeps tests out of the user's cache. An entry's key is a SHA-256 of the class's bytes, as they are sent to the decompiler, and of the
decompiler: its format number, and where FernFlower, the patched `ClassWrapper` and `JetBrainsDecompilerService` were
loaded from, with their sizes and times. `DecompilerCacheService` looks there before decompiling a class and writes
there after, on its decompiler thread; the in-memory cache stays in front of it.

Entries are gzipped Java with FernFlower's line comments, as the worker returns it, written to a temporary file that
is moved into place. When the entries pass the size limit, 500 MB unless another is chosen, the ones used least
recently are deleted down to four fifths of it. A read
or write that fails is a miss, logged, never a failure.

## Consequences

- A class decompiled before opens at once: the largest class of a game client in 0.1 seconds, and one of 200 MB of
  Java in 3, instead of 9 to 19.
- Nothing is invalidated: a renamed class has other bytes and is decompiled again, and a rebuilt or upgraded
  decompiler has another key. Changing the options the decompiler runs with, or anything else that changes its
  Java without changing those classes, needs `SourceCache.FORMAT` changed.
- Decompiled code of the JARs someone opens stays on their disk until it is deleted for room, or they delete the
  folder. Choosing another folder leaves the old one as it is; Settings says so.
- Tests keep their entries in `target/test-decompiled`, which `pom.xml` sets for surefire.
