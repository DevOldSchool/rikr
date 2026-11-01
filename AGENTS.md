# Working on Rikr

Rikr is a Swing workbench for reverse engineering Java bytecode, mostly obfuscated JARs such as game clients. It
loads JARs, shows ASM bytecode and decompiled Java, follows references, renames classes, fields and methods, maps
classes between versions of a program, and reads and writes mapping files. The same features are available to
agents through an MCP server (see [Using Rikr from an agent](#using-rikr-from-an-agent)).

## Build, test and run

Java 21 and Maven. All dependencies are in Maven Central and the IntelliJ repository; add `-o` when offline.

| Task | Command |
|---|---|
| Run the tests | `mvn -q test` |
| Everything CI checks: Error Prone, tests, Checkstyle, Spotless, coverage | `mvn -q verify` |
| Fix formatting Spotless reports (unused imports, whitespace) | `mvn -q spotless:apply` |
| Build classes and copy dependencies to `target/lib` | `mvn -q -DskipTests package` |
| Start the GUI | `java -cp "target/classes:target/lib/*" org.de.rikr.Rikr` (after `package`) |
| Start the MCP server | `bin/rikr-mcp` |
| Check the patched FernFlower classes against their diffs (after changing it or `fernflower.version`) | `bin/check-patches` |

Before handing work back, run `mvn -q verify`. It compiles with Error Prone, which fails the build on its error-level
bug patterns; runs the tests, Checkstyle (spaces, not tabs, and a final newline) and Spotless; and checks that the
`rename`, `mapping`, `flow` and `symbol` packages keep at least 85% line and 55% branch coverage (report in
`target/site/jacoco`). `.editorconfig` asks for 4-space indents and lines of at most 120 characters.

Pushing a tag such as `v1.2.0` runs `.github/workflows/release.yml`, which builds macOS, Windows and Linux packages
with `jpackage` and publishes a GitHub release.

## How the code is organised

Code is grouped by feature under `src/main/java/org/de/rikr`, with tests in the matching package:

| Package | What it holds |
|---|---|
| `project` | `Project`: one loaded project, its `EventBus`, and the feature services `JarOperations`, `RenameOperations`, `MappingOperations` and `Bookmarks`. Project files (`ProjectSessionService`, JSON in `ProjectFileJson`, old properties files in `LegacyProjectFile`). |
| `model` | `Workspace` (classes by JAR), `ClassRef`, `OriginalNames` (names as loaded). |
| `rename` | `RenameEngine` (ASM `Remapper` passes), `RenameService` (validation, history, undo, replay), `RenameStep` = `RenameOperation` or `RenameBatch`. |
| `symbol` | Symbols recorded while printing bytecode, so a click resolves to a class, field or method. |
| `flow` | Following code: `EntryPoints` (where a JAR starts, including obfuscated applets), `CallGraph` (calls with override dispatch), `ControlFlowBuilder` (basic blocks and edges), and `GraphLayout` (layered layout). Drawn by `ui/FlowPanel` with `ui/graph/GraphView`. |
| `behavioral` | Simulating a method without running it: `MethodSimulator` steps through it (with undo), `Interpreter` gives each opcode its exact stack effect, and values it can't know are a typed `SimulatedValue.Unknown`, never a guess. `MethodComparator` runs two methods side by side for class matching. Calls aren't followed. |
| `mapping` | Class matching between JARs (`MappingSet`, `ClassMappingService`), more class matches from matched members (`ClassVoting`), member matching within matched classes (`MemberMatcher`, used to carry names to a new version through `RenameOperations.planNameTransfer`, which skips made-up names such as `class12` via `GeneratedNames`), and `mapping/format` for tiny v2, Enigma and ProGuard files. |
| `jar` | Reading JARs and class files, and `JarExportService` for safe export. |
| `reference`, `search`, `decompiler`, `bookmark` | The features their names say. The decompiler is JetBrains FernFlower, with a few classes patched under `src/main/java/org/jetbrains` ([decision 0010](docs/decisions/0010-patched-fernflower-class.md)) so that, among other things, a class's methods are decompiled in parallel ([decision 0012](docs/decisions/0012-methods-decompiled-in-parallel.md)), run in a JVM of its own by `WorkerDecompiler` ([decision 0011](docs/decisions/0011-decompiler-in-its-own-jvm.md)), with the Java kept on disk by `SourceCache` ([decision 0013](docs/decisions/0013-decompiled-java-cached-on-disk.md)). |
| `task` | `TaskRunner`: background work with results delivered on the Swing event thread. |
| `agent` | The MCP server. |
| `ui` | Swing: `ClassViewer` (main window), `CodeView` (with the `EmptyStatePanel` start screen), `TreePanel` (grouped by package), `StatusBar`, `GoToDialog` (Cmd+K), `NavigationHistory` (Back and Forward), `MenuBar` (every shortcut), the workspace panels. `Rikr` is the GUI's application shell. |

## Rules the design depends on

- **One thread changes a project.** In the GUI that is the Swing event thread; in the MCP server it is the server's
  thread. Slow work (reading JARs, opening projects, writing JARs) runs on `TaskRunner` and must not touch the
  project: it returns a result that the event thread applies. Take snapshots (`JarOperations.snapshot`) on the
  owning thread before handing data to another one.
- **Changes are announced on the project's `EventBus`, synchronously.** `Project` subscribes first, so indexes,
  caches, class mappings and bookmarks are up to date before the UI's listener runs. New derived state should
  update from `Project.onEvent`, not from UI code.
- **Renames change nodes in place.** `ClassNode`, `FieldNode` and `MethodNode` objects keep their identity, which is
  what `OriginalNames`, bookmarks and open views rely on. Every rename goes through `RenameService` so it is
  validated, recorded for undo, and replayed when a project is reopened. A mapping import is one `RenameBatch`
  whose names are all as they were before the batch.
- **The project file format is versioned.** `ProjectFileJson` has its own records, separate from the domain types.
  Changing what is saved means changing those records and `FORMAT_VERSION`, and keeping a way to read older files.
- **JAR export never leaves a half-written file.** Writes go to a temporary file that is moved into place;
  overwriting keeps a `.bak` copy made before anything is written.
- **On macOS the menu bar is the system menu bar.** `Rikr.main` sets `apple.laf.useScreenMenuBar` before Swing
  starts; anything else that starts the GUI must do the same.

The reasoning behind these rules is in [docs/decisions](docs/decisions/README.md). Add a record there when a change
sets a new rule.

## Conventions

- **Shortcuts follow macOS conventions** and live in `MenuBar`: Cmd+S saves the project, Cmd+G finds the next
  match. Actions that change files outside the project, such as overwriting a JAR, have no shortcut and confirm
  first, naming the file and what is lost.
- Java 21: records, sealed interfaces and pattern-matching `switch` are used throughout.
- Comments explain why, not what. Javadoc on public types and on methods whose contract is not obvious.
- Tests: JUnit 5. Bytecode tests build classes with ASM's tree API; `RenameEngineTest` has a small program that is
  renamed and then run, to prove renamed bytecode still verifies and dispatches correctly.
- Leave changes uncommitted: the maintainer reviews and commits. Propose a commit message instead.

## Using Rikr from an agent

`bin/rikr-mcp` starts an MCP server on stdin and stdout (building the project first if needed). It holds one
headless project per session. `.mcp.json` registers it for Claude Code in this repository.

It speaks MCP 2026-07-28, where each request carries its protocol version in `_meta`, and the older handshake
revisions from 2024-11-05 to 2025-11-25, which start with `initialize`. `McpServer` picks the behaviour per
request, as the specification's dual-era rules describe.

The tools: `load_jars`, `remove_jar`, `list_jars`, `list_classes`, `describe_class`, `get_bytecode`, `decompile`,
`search`, `find_references`, `list_entry_points`, `naming_progress`, `get_call_graph`, `get_control_flow`, `rename`, `undo`, `redo`, `rename_history`, `import_mappings`, `export_mappings`, `export_jar`,
`overwrite_original_jar`, `match_classes`, `accept_class_mapping`, `auto_match_classes`, `match_members`,
`carry_names`, `list_class_mappings`,
`open_project` and `save_project`. Class names are internal (`a/b/C`); dotted names are accepted too. Renames and
imports take `dry_run`. Nothing on disk changes until a save or export tool is called.

When changing Rikr itself:

- A new feature should get a tool in `agent/RikrTools.java` if an agent could use it: add it to `tools()` with a
  `Schema` describing its arguments, and a test in `RikrMcpTest`.
- Tool errors the caller can fix throw `ToolException`, whose message is returned to the agent; say what to do
  instead (for example, which names are loaded).
- The server must write nothing but protocol messages to stdout. Diagnostics go to stderr; `RikrMcp` redirects
  `System.out` for that reason.
- After changing the code, run `mvn -q -DskipTests package` and restart the MCP server so it uses the new classes.
