# Rikr

A software reverse engineering suite of tools for Java.

## Building and running

Rikr needs Java 21 and Maven.

```bash
mvn -q -DskipTests package
java -cp "target/classes:target/lib/*" org.de.rikr.Rikr
```

`mvn verify` runs everything CI checks: the tests, Error Prone, Checkstyle, Spotless and a coverage report in
`target/site/jacoco`. Design decisions are recorded in [docs/decisions](docs/decisions/README.md).

Pushing a tag such as `v1.2.0` publishes a GitHub release with packages that include their own Java runtime:

- macOS (Apple silicon): a `.dmg`.
- Windows: a `.zip`; extract it and run `Rikr\Rikr.exe`.
- Linux: a `.tar.gz` that runs on any 64-bit distribution (extract it and run `Rikr/bin/Rikr`), plus a `.deb` for
  Debian and Ubuntu and an `.rpm` for Fedora and openSUSE.

## Using Rikr from AI agents

`bin/rikr-mcp` runs Rikr as a [Model Context Protocol](https://modelcontextprotocol.io) server, so agents such as
Claude Code can load JARs, read bytecode and decompiled Java, follow references, rename classes and members, match
classes between JARs, and import or export tiny v2, Enigma and ProGuard mappings without the GUI. This repository's
`.mcp.json` registers it for Claude Code; for another client, run `bin/rikr-mcp` as a stdio server.

See [AGENTS.md](AGENTS.md) for how the code is organised and the rules agents working on it should follow.

## Roadmap

The following items are listed in no particular order and are here as a todo list.

#### Saving

- ~~Save your project.~~
- ~~Save changes to class files in Jar.~~

#### User interface to step through instructions.

- ~~Step through the instructions in a method.~~
- ~~View the stack as you go.~~
- ~~View local variables as you go.~~
- ~~Visually show the control flow.~~

#### User interface for class mapping between Jars.

- ~~View class information and matching class information.~~
    - ~~Field count, method count, interface count.~~
- ~~List of classes based on percentage matching.~~
- ~~Accept or reject one-to-one class mappings.~~
- ~~Auto-match unique candidates above a configurable threshold.~~
- ~~Export accepted mappings as JSON.~~

#### Global search

- ~~Search through all class nodes across all loaded jars including instructions.~~
- ~~Search filters, include static, include instructions.~~
- ~~Search result hierarchy, class, field, method, instruction.~~

#### References and call hierarchy

- ~~Navigate method callers and callees.~~
- ~~Separate field reads from field writes.~~
- ~~Find inheritance, descriptor, and bytecode type references.~~

#### Control flow graph

- ~~Follow code from jar entry point.~~
- ~~Load control flow from selected point.~~

#### Renaming and mappings

- ~~Rename classes, fields and methods with a preview and undo.~~
- ~~Rename by clicking a name in the bytecode view.~~
- ~~Export renames as tiny v2, Enigma or ProGuard mappings.~~
- ~~Import tiny v2, Enigma or ProGuard mappings as one undoable step.~~

#### AI agents

- ~~Use Rikr's features from agents through an MCP server.~~

#### Settings

- ~~Set font and size.~~
- ~~Set theme.~~
- ~~Set syntax highlight colors.~~

#### Application icon

#### Documentation

- ~~Building.~~
- ~~Running.~~
- User guides.

## Cross-JAR mapping workspace

Load at least two JARs, then open **View > Cross-JAR Mappings** or press
`Ctrl/Cmd+Shift+M`. Select source and target JARs to review ranked structural
matches with side-by-side field, method, interface, and superclass details.

Accepted mappings are one-to-one within a JAR pair. Rejected candidates are
remembered for the current session, and **Auto-match unique** only accepts a
source class when its highest-scoring available candidate is unambiguous.
Accepted mappings can be exported as JSON.

## References and call hierarchy

Right-click a class, field, or method in the project tree and choose
**Show References**. Classes show inheritance, descriptor, and bytecode type
references; fields separate reads from writes; methods separate callers from
callees. Double-click a result, or select it and press Enter, to open and
highlight the relevant member. The reference index is rebuilt when JAR content
changes, so queries do not repeatedly scan every instruction.

## Project sessions

Use **File > Save Project** to create a `.rikr` session, saved as versioned JSON;
project files from older versions of Rikr still open. A project records the
absolute paths of loaded JARs, accepted cross-JAR mappings, rename history,
bookmarks, window bounds, split positions, visible tools, and the selected
workspace. Opening a project validates its JAR paths and restores into a staged
workspace, so a failed load does not replace the project already open.

Right-click a class, field, or method and choose **Toggle Bookmark**, then open
**View > Bookmarks** (`Ctrl/Cmd+Shift+B`) to navigate saved locations.

## Safe renaming

Renames are previewed before they change bytecode. The preview validates JVM
names and member/class collisions and shows the number of affected references
and classes. Use **Edit > Undo Rename** (`Ctrl/Cmd+Z`) to reverse the latest
rename. A `*` beside a JAR marks bytecode with changes that have not been
exported; saving or overwriting that JAR clears the marker.

In the bytecode view, right-click a class, field or method name and choose
**Rename** to rename exactly that symbol. Exported JARs keep the original's
resources and manifest; **Overwrite Original JAR** keeps the previous file as
`<name>.bak`.

## Rename mappings

Right-click a JAR and choose **Export Rename Mappings...** to save its renames
as a tiny v2, Enigma or ProGuard file, or **Import Rename Mappings...** to apply
one. Mapping files name classes and members as they were when the JAR was
loaded. An import is checked before anything changes, shows what it will
rename, and is undone in one step.

## Entry points, call graphs and control flow

Open the **C** tab, or right-click a JAR and choose **Show Entry Points**, then
pick where the code starts: the manifest's Main-Class, `main` methods, applets
(including obfuscated ones), thread `run()` methods or event listeners. The
call graph that opens shows what the method calls; click a method's badge to
show its calls, and double-click it for its control flow graph of basic blocks.
In a control flow graph, right-click a block to follow the methods it calls,
and double-click it to see it in the bytecode view. **Back** retraces the path.
A method's right-click menu in the project tree also has **Show Call Graph** and
**Show Control Flow**.
