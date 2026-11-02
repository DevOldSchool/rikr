# Renaming and mapping files

## Rename

Select a class, field or method in the project tree, or put the caret on its name in the bytecode view, and choose
**Code > Rename…** (`Shift+F6`) or **Rename…** from the right-click menu. In the bytecode view this renames exactly
the symbol under the caret. The name is checked as it is typed: a name that is not a valid JVM name, or that collides
with another member or class, is said under the field, and a usable one shows how many references and classes change
(hover over that line for the classes). Enter renames. Every reference in the JAR is updated, including overrides.

**Edit > Undo** (`Cmd+Z`) reverses the latest rename or import, and **Edit > Redo** (`Shift+Cmd+Z`) applies it again;
the menu names the step, such as "Undo Rename y.c → value". A new rename clears Redo. A `*` beside a JAR marks
bytecode with changes that have not been exported.

## Track naming progress

**View > Naming** (`Shift+Cmd+N`) shows how many of a JAR's classes have been given names, and lists the unnamed
classes most referred to, which are usually the best ones to name next. Choosing one opens it; **Rename…** names it
and the list moves on, and **Skip** passes over it.

## Import and export mappings

Right-click a JAR and choose **Export Rename Mappings...** to save its renames as a tiny v2, Enigma or ProGuard file,
or **Import Rename Mappings...** to apply one. Mapping files name classes and members as they were when the JAR was
loaded. An import is checked before anything changes, shows what it will rename, and is undone in one step.

## Export the renamed JAR

**File > Export JAR...** (`Alt+Cmd+S`) writes the renamed JAR to a new file, keeping the original's resources and
manifest. **File > Export Class...** writes the open class. **File > Overwrite Original JAR...** asks first, then keeps
the previous file as `<name>.bak`. A JAR is never left half-written: it is written to a temporary file that is moved
into place.

Removing a JAR from the project asks first and says how many renames, mappings and bookmarks go with it.
