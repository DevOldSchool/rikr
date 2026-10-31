# 0010: One FernFlower class is patched in Rikr

**Status:** accepted

## Context

Obfuscators write array code that never runs, such as `new T[940935738]` filled with 135 elements, or stores to
negative indices. FernFlower turns array stores into an initializer listing every element, so for the first it built
an expression for each of the 940 million slots until the heap was full, and for the second it threw. Opening such a
class took a minute and several gigabytes, and decompiling a whole game client ran out of memory even with 10 GB.
Vineflower has the same code. FernFlower takes no issues or pull requests on GitHub, and fixes through YouTrack and
intellij-community are slow and uncertain.

## Decision

`src/main/java/org/jetbrains/java/decompiler/modules/decompiler/SimplifyExprentsHelper.java` is a copy of
FernFlower's class with the changes marked `Rikr:`. It leaves out initializers that would leave more than 1,000
elements unset, and ignores stores to negative indices. The build compiles it with Rikr, leaves it out of Rikr's
JAR, and puts it in place of the original in the decompiler JAR it copies to `target/lib`, so the GUI, the MCP server
and the packaged app all use it whatever order their class path has. Error Prone and Spotless skip it, to keep it as
close to JetBrains' code as possible.

`patches/fernflower/SimplifyExprentsHelper.patch` is the diff from JetBrains' source for `fernflower.version` to that
copy, so the change can be reviewed, carried to a new version and sent upstream on its own. `bin/check-patches`
downloads that source and checks that it with the diff applied is the copy. The build keeps compiling the copy rather
than applying the diff, so it needs no source download and works offline.

## Consequences

- Updating `fernflower.version` means running `bin/check-patches upgrade`, which applies the diff to the new
  version's source and writes the copy, or stops if the diff no longer applies; an old copy over a new engine could
  break it. After changing the copy, `bin/check-patches update` writes the diff again. `JetBrainsDecompilerServiceTest`
  fails if the fix is lost.
- Such arrays are shown as a `new T[n]` and a store per element, as the bytecode has them.
- When FernFlower or Vineflower fixes this, the copy can go.
