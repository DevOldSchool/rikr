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

#### ~~Application icon~~

#### Documentation

- ~~Building.~~
- ~~Running.~~
- User guides.

## Finding your way

Open a JAR with **File > Open JAR or Class...**, by dropping `.jar` and `.class`
files on the window, or from the recent projects on the start screen. The
project tree groups classes by package; each JAR shows how many classes it has.

**Navigate > Go to Class or Member...** (`Cmd+K`, `Ctrl+K` elsewhere) finds a
class, field or method by part of its name across every loaded JAR. With nothing
typed it lists the classes opened most recently. **Navigate > Back** and
**Forward** (`Cmd+[` and `Cmd+]`) move through the classes you opened.

The bar above the code names the open class and its JAR, and the project tree
selects it wherever it was opened from. With **View > Split Bytecode and Java**
on, click or move the caret with the keyboard in either view to show the same
code in the other. A field shows its
declaration. Inside a method, the class file's line numbers find the Java line
an instruction was decompiled into, and the instruction is matched to what it
reads as: a load to its variable, a call or field to its name, a return to
`return`, a constant to its value, `IMUL` to `*` and the other arithmetic to
their operators, a cast to its type, and a branch to its `if`, `for`, `while`
or `?`.
Clicking Java works the other way. Without line numbers the whole method is
searched, and what cannot be matched shows the line or the method.

**Edit > Find in Class...** (`Cmd+F`) searches the open class; **Find Next**
and **Find Previous** are `Cmd+G` and `Shift+Cmd+G`. In every search field,
`Alt+C` and `Alt+W` switch Match Case and Match Words. The status bar shows the
latest message and when background work is running; click it, or press `Cmd+L`,
to show the full log.

| Shortcut | Action |
|---|---|
| `Cmd+O` / `Shift+Cmd+O` | Open JAR or class / open project |
| `Cmd+S` / `Shift+Cmd+S` | Save project / save project as |
| `Ctrl+Cmd+S` / `Alt+Cmd+S` | Export class / export JAR |
| `Cmd+Z` | Undo rename |
| `Cmd+F`, `Cmd+G`, `Shift+Cmd+G` | Find in class, next and previous match |
| `Cmd+K` | Go to class or member |
| `Cmd+[` / `Cmd+]` | Back / forward |
| `Shift+Cmd+E`, `F`, `M`, `R`, `B`, `C` | Project, Search, Cross-JAR Mappings, References, Bookmarks, Entry Points and Flow |
| `Shift+Cmd+J` | Split bytecode and Java |
| `Cmd+L` | Show or hide the log |

On Windows and Linux, use `Ctrl` in place of `Cmd`.

## Cross-JAR mapping workspace

Load at least two JARs, then open **View > Cross-JAR Mappings** or press
`Ctrl/Cmd+Shift+M`. Select source and target JARs to review ranked structural
matches with side-by-side field, method, interface, and superclass details.

The source list marks mapped classes and can be filtered by name or to the
classes not yet mapped, with a count of how many are mapped. Members in the
details with no member of the same shape in the other class are highlighted.
Press `Enter` to accept the selected candidate and move to the next unmapped
class, or `Delete` to reject it.

Accepted mappings are one-to-one within a JAR pair. Rejected candidates are
remembered for the current session, and **Auto-match unique** only accepts a
source class when its highest-scoring available candidate is unambiguous.
Accepted mappings can be exported as JSON.

## References and call hierarchy

Right-click a class, field, or method in the project tree and choose
**Show References**. Classes show inheritance, descriptor, and bytecode type
references; fields separate reads from writes; methods separate callers from
callees. Results are grouped by the class they are in, with each member's
parameter and return types and how it uses the target. Double-click a result,
or select it and press Enter, to open and highlight the relevant member;
double-click a class heading to open the class. **Find Extenders**, **Find
Implementors** and **Find Matching Class Nodes** list their results here too.
Tick **Follow the open class** to show the references of each class you open. The reference index is rebuilt when JAR content
changes, so queries do not repeatedly scan every instruction.

## Project sessions

Use **File > Save Project** (`Cmd+S`) to create a `.rikr` session, saved as versioned JSON;
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
resources and manifest; **Overwrite Original JAR** asks first, then keeps the
previous file as `<name>.bak`. Removing a JAR from the project asks first and
says how many renames, mappings and bookmarks go with it.

## Rename mappings

Right-click a JAR and choose **Export Rename Mappings...** to save its renames
as a tiny v2, Enigma or ProGuard file, or **Import Rename Mappings...** to apply
one. Mapping files name classes and members as they were when the JAR was
loaded. An import is checked before anything changes, shows what it will
rename, and is undone in one step.

## Entry points, call graphs and control flow

Open **Entry Points and Flow** (the branching icon in the left rail, `Shift+Cmd+C`), or right-click a JAR and choose **Show Entry Points**, then
pick where the code starts: the manifest's Main-Class, `main` methods, applets
(including obfuscated ones), thread `run()` methods or event listeners. The
call graph that opens shows what the method calls; click a method's badge to
show its calls, and double-click it for its control flow graph of basic blocks.
In a control flow graph, right-click a block to follow the methods it calls,
and double-click it to see it in the bytecode view. **Back** retraces the path.
A method's right-click menu in the project tree also has **Show Call Graph** and
**Show Control Flow**.

**Fit** shows the whole graph when its text stays readable; a larger graph is
fitted to its width and opens at the top, with an overview in the corner to
click or drag around. Type in **Find in graph** and press `Enter` to go to the
next block or method containing that text.
