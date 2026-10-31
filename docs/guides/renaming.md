# Renaming and mapping files

## Rename

Right-click a class, field or method in the project tree and choose **Rename**, or right-click its name in the
bytecode view to rename exactly that symbol. Renames are previewed before they change bytecode: the preview checks
JVM names and collisions with other members and classes, and shows how many references and classes change. Every
reference in the JAR is updated, including overrides.

**Edit > Undo Rename** (`Cmd+Z`) reverses the latest rename. A `*` beside a JAR marks bytecode with changes that have
not been exported.

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
