# 0011: The decompiler runs in a JVM of its own

**Status:** accepted

## Context

FernFlower is slow and memory-hungry on obfuscated code: the largest class of a game client takes 20 to 50 seconds
and close to 1 GB, and code it can't handle has filled a 10 GB heap (decision 0010 fixes the case found so far). In
Rikr's JVM that memory and the garbage collection it causes slow the whole application, and a class that never
finishes can't be stopped, since a Java thread can't be killed.

## Decision

`WorkerDecompiler` starts `DecompilerWorker` in a JVM of its own, with this runtime's `java` launcher, Rikr's class
path and a 1 GB heap, when the first class is decompiled, and keeps it for the next. Classes go to it on stdin and
their Java comes back on stdout, one at a time; its log messages come back with the Java and are logged as before.
A class that takes longer than 90 seconds is stopped by ending the process, and the next class starts a new one. The
worker ends when Rikr closes its stdin, so it never outlives Rikr.

When the runtime has no launcher, or the process can't start, classes are decompiled in Rikr's JVM as before. The
release keeps the launcher: jpackage runs with its own jlink options minus `--strip-native-commands`.

## Consequences

- The first class decompiled waits for a JVM to start, under a second.
- The worker gets bytes, not class nodes, so it can't see renames made meanwhile; `DecompilerCacheService` already
  discards results from before a rename.
- Anything else that starts Rikr from a custom runtime must keep `bin/java`, or decompiling falls back to Rikr's JVM.
- A JVM that has just started runs FernFlower slowly until the JIT has compiled it: a game client's largest class
  took 12 seconds as the first class and 9 after others. So when JARs are loaded, the GUI and the MCP server send the
  worker a few of their classes to decompile in the background (`Project.warmUpDecompiler`); the worker gives that
  up as soon as a class is asked for. `Project` doesn't do it itself, so tests don't start workers.
