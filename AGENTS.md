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
| Check style (not yet part of `verify`) | `mvn -q checkstyle:check` |
| Build classes and copy dependencies to `target/lib` | `mvn -q -DskipTests package` |
| Start the GUI | `java -cp "target/classes:target/lib/*" org.de.rikr.Rikr` (after `package`) |
| Start the MCP server | `bin/rikr-mcp` |

Before handing work back, run the tests and Checkstyle. Checkstyle enforces spaces, not tabs, and a final newline;
`.editorconfig` asks for 4-space indents and lines of at most 120 characters.

## How the code is organised

Code is grouped by feature under `src/main/java/org/de/rikr`, with tests in the matching package:

| Package | What it holds |
|---|---|
| `project` | `Project`: one loaded project, its `EventBus`, and the feature services `JarOperations`, `RenameOperations`, `MappingOperations` and `Bookmarks`. Project files (`ProjectSessionService`, JSON in `ProjectFileJson`, old properties files in `LegacyProjectFile`). |
| `model` | `Workspace` (classes by JAR), `ClassRef`, `OriginalNames` (names as loaded). |
| `rename` | `RenameEngine` (ASM `Remapper` passes), `RenameService` (validation, history, undo, replay), `RenameStep` = `RenameOperation` or `RenameBatch`. |
| `symbol` | Symbols recorded while printing bytecode, so a click resolves to a class, field or method. |
| `mapping` | Class matching between JARs (`MappingSet`, `ClassMappingService`) and `mapping/format` for tiny v2, Enigma and ProGuard files. |
| `jar` | Reading JARs and class files, and `JarExportService` for safe export. |
| `reference`, `search`, `decompiler`, `bookmark` | The features their names say. The decompiler is JetBrains FernFlower. |
| `task` | `TaskRunner`: background work with results delivered on the Swing event thread. |
| `agent` | The MCP server. |
| `ui` | Swing: `ClassViewer` (main window), `CodeView`, `TreePanel`, the workspace panels. `Rikr` is the GUI's application shell. |

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

## Conventions

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
`search`, `find_references`, `rename`, `undo`, `rename_history`, `import_mappings`, `export_mappings`, `export_jar`,
`overwrite_original_jar`, `match_classes`, `accept_class_mapping`, `auto_match_classes`, `list_class_mappings`,
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
