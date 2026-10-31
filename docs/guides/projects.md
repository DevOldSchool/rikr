# Projects and bookmarks

## Projects

**File > Save Project** (`Cmd+S`) saves a `.rikr` project, and **File > Open Project...** (`Shift+Cmd+O`) opens one.
A project records the JARs loaded, accepted class mappings, rename history, bookmarks, and the window's layout and
open workspace. Renames are replayed when a project is opened, so the JAR files themselves don't change until you
export them.

Opening a project checks its JAR paths first, so a project that fails to open doesn't replace the one already open.
Project files from older versions of Rikr still open.

## Bookmarks

Right-click a class, field or method and choose **Toggle Bookmark**, then open **View > Bookmarks** (`Shift+Cmd+B`)
to go back to saved places. Bookmarks follow their members through renames.
