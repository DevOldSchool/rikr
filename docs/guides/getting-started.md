# Getting started

## Open a JAR

Open a JAR with **File > Open JAR or Class...** (`Cmd+O`), by dropping `.jar` and `.class` files on the window, or
from the recent projects on the start screen. The project tree groups classes by package; each JAR shows how many
classes it has. **View > Group by Superclass** and **Group by Interface** regroup it.

## Read code

Click a class to open it. The bar above the code names the open class and its JAR, and the project tree selects it
wherever it was opened from.

With **View > Split Bytecode and Java** (`Shift+Cmd+J`) on, bytecode and decompiled Java show side by side. Click or
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

The status bar shows the latest message and when background work is running; click it, or press `Cmd+L`, to show the
full log. Font, size, theme and syntax colors are in **Settings**.

## Shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+O` / `Shift+Cmd+O` | Open JAR or class / open project |
| `Cmd+S` / `Shift+Cmd+S` | Save project / save project as |
| `Ctrl+Cmd+S` / `Alt+Cmd+S` | Export class / export JAR |
| `Cmd+Z` | Undo rename |
| `Cmd+F`, `Cmd+G`, `Shift+Cmd+G` | Find in class, next and previous match |
| `Cmd+K` | Go to class or member |
| `Cmd+[` / `Cmd+]` | Back / forward |
| `Shift+Cmd+E`, `F`, `M`, `R`, `C`, `B` | Project, Search, Cross-JAR Mappings, References, Entry Points and Flow, Bookmarks |
| `Shift+Cmd+J` | Split bytecode and Java |
| `Cmd+L` | Show or hide the log |

On Windows and Linux, use `Ctrl` in place of `Cmd`.
