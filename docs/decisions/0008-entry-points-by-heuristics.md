# 0008: Entry points are found by heuristics, most likely first

**Status:** accepted

## Context

To follow code from where a program starts, Rikr needs its entry point. Obfuscated game clients often have no
`main` and no Main-Class: they are applets whose lifecycle classes do not even extend `java.applet.Applet`.

## Decision

`EntryPoints` lists every likely entry, ranked: the manifest's Main-Class, static `main` methods, applets (real
ones, or concrete classes that have `init`, `start`, `stop` and `destroy`), thread `run()` methods (the applet's own
`run()` first), and event listener methods. JDK supertypes are checked by loading them from the platform class
loader. Call graphs (`CallGraph`) resolve calls to declarations and add the overrides a virtual call may reach.

## Consequences

- The user, or an agent, chooses among likely entries; nothing assumes there is exactly one.
- Some links are invisible to static analysis: a thread started through `java.lang.Thread` does not connect
  `start()` to `run()`, so `run()` is listed as its own entry point.
