# Decision records

Short records of the larger design choices in Rikr: what was decided, why, and what it costs. Add one when a change
sets a rule other code has to follow. Number them in order and never reuse a number; when a decision changes, write
a new record and mark the old one as replaced.

| # | Decision |
|---|---|
| [0001](0001-project-and-feature-packages.md) | A project is one object, and code is grouped by feature |
| [0002](0002-synchronous-event-bus.md) | Changes are announced on a synchronous event bus, project first |
| [0003](0003-one-thread-changes-a-project.md) | One thread changes a project; slow work returns results to it |
| [0004](0004-renames-in-place-as-recorded-steps.md) | Renames change nodes in place and are recorded as replayable steps |
| [0005](0005-versioned-json-project-files.md) | Project files are versioned JSON with their own records |
| [0006](0006-symbols-recorded-while-printing.md) | The bytecode view records symbols while printing |
| [0007](0007-mcp-server-over-stdio.md) | Agents use Rikr through its own MCP server over stdio |
| [0008](0008-entry-points-by-heuristics.md) | Entry points are found by heuristics, most likely first |
| [0009](0009-current-method-shared-by-workspaces.md) | The chosen method is current for every workspace |
| [0010](0010-patched-fernflower-class.md) | One FernFlower class is patched in Rikr |
| [0011](0011-decompiler-in-its-own-jvm.md) | The decompiler runs in a JVM of its own |
| [0012](0012-methods-decompiled-in-parallel.md) | A class's methods are decompiled in parallel |
| [0013](0013-decompiled-java-cached-on-disk.md) | Decompiled Java is kept on disk by class bytes |
