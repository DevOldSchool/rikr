# Projects and bookmarks

## Projects

**File > Save Project** (`Cmd+S`) saves a `.rikr` project, and **File > Open Project...** (`Shift+Cmd+O`) opens one.
A project records the JARs loaded, accepted class mappings, rename history, bookmarks, and the window's layout and
open workspace. Renames are replayed when a project is opened, so the JAR files themselves don't change until you
export them.

Opening a project checks its JAR paths first, so a project that fails to open doesn't replace the one already open.
Project files from older versions of Rikr still open.

When renames, class mappings or bookmarks are unsaved, the window shows it as edited, and closing the window, quitting
or opening another project asks whether to save first.

## Bookmarks

Choose **Code > Add or Remove Bookmark** (`F3`) on a class, field or method, or **Add Bookmark** from its right-click
menu, then open **View > Bookmarks** (`Shift+Cmd+B`) to go back to saved places. Bookmarks follow their members
through renames.
