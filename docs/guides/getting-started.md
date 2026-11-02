# Getting started

## Open a JAR

Open a JAR with **File > Open JAR or Class...** (`Cmd+O`), by dropping `.jar` and `.class` files on the window, or
from the recent projects on the start screen. The project tree groups classes by package; each JAR shows how many
classes it has. **View > Group by Superclass** and **Group by Interface** regroup it, and **Remove Grouping** goes
back. **Edit > Strip Annotations When Loading** leaves annotations out of JARs loaded after it is ticked.

## Read code

Click a class to open it. The bar above the code names the open class and its JAR, and the project tree selects it
wherever it was opened from.

The **Bytecode** / **Bytecode and Java** switch in that bar, or **View > Split Bytecode and Java** (`Shift+Cmd+J`),
shows bytecode and decompiled Java side by side. Click or
move the caret in either view to show the same code in the other. A field shows its declaration. Inside a method, the
class file's line numbers find the Java line an instruction was decompiled into, and the instruction is matched to
what it reads as: a load to its variable, a call or field to its name, a return to `return`, a constant to its value,
`IMUL` to `*` and the other arithmetic to their operators, a cast to its type, and a branch to its `if`, `for`,
`while` or `?`. Without line numbers the whole method is searched, and what cannot be matched shows the line or the
method.

## Find your way

**Navigate > Go to Class or Member...** (`Cmd+K`) finds a class, field or method by part of its name across every
loaded JAR. With nothing typed it lists the classes opened most recently. **Navigate > Back** and **Forward**
(`Cmd+[` and `Cmd+]`) move through the classes you opened.

**Edit > Find in Class...** (`Cmd+F`) searches the open class; **Find Next** and **Find Previous** are `Cmd+G` and
`Shift+Cmd+G`. In every search field, `Alt+C` and `Alt+W` switch Match Case and Match Words.

**View > Search** (`Shift+Cmd+F`) searches names, strings and member references in every loaded JAR, including
instructions. Results are grouped by class and member; the chips above them show only classes, members or
instructions.

## Act on code

The **Code** menu acts on the class, field or method selected in the project tree, or on the name at the caret in the
code, whichever has the focus: **Rename…** (`Shift+F6`), **Show References** (`Alt+F7`), **Add or Remove Bookmark**
(`F3`), **Show Control Flow** (`Alt+F8`), **Show Call Graph** (`Alt+F9`) and **Simulate Method**. The same actions are
on the right-click menu of the tree and of the code; `Shift+F10` opens it from the keyboard.

## Log and settings

The status bar shows the latest message and when background work is running; click it, or use **Window > Show or Hide
Log** (`Cmd+L`), to see the full log. The log's header filters its lines, copies them and clears it. The editor font, its size, the theme and the decompiled Java cache are in
**Settings** (`Cmd+,`): in the Rikr menu on macOS, and in the Edit menu elsewhere. **View > Dim Annotations** shows
annotation lines in the comment colour, so the declarations between them stand out.

## Shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+O` / `Shift+Cmd+O` | Open JAR or class / open project |
| `Cmd+S` / `Shift+Cmd+S` | Save project / save project as |
| `Ctrl+Cmd+S` / `Alt+Cmd+S` | Export class / export JAR (on Windows and Linux, `Ctrl+Alt+Shift+S` / `Ctrl+Alt+S`) |
| `Cmd+Z` / `Shift+Cmd+Z` | Undo / redo the last rename or import (typing, in a text field) |
| `Cmd+F`, `Cmd+G`, `Shift+Cmd+G` | Find in class, next and previous match |
| `Cmd+K` | Go to class or member |
| `Cmd+[` / `Cmd+]` | Back / forward |
| `Shift+Cmd+E`, `F`, `M`, `R`, `C`, `N`, `B` | Project, Search, Cross-JAR Mappings, References, Entry Points and Flow, Naming, Bookmarks |
| `Shift+Cmd+J` | Split bytecode and Java |
| `Shift+F6` / `Alt+F7` / `F3` | Rename / show references / add or remove bookmark |
| `Alt+F8` / `Alt+F9` | Show control flow / show call graph |
| `Shift+F10` | Open the right-click menu |
| `Cmd+,` | Settings |
| `Cmd+L` | Show or hide the log |

On Windows and Linux, use `Ctrl` in place of `Cmd`. On macOS, `Alt` is the Option key.
