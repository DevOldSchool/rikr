# References and call hierarchy

Right-click a class, field or method in the project tree and choose **Show References**, or open **View > References
and Call Hierarchy** (`Shift+Cmd+R`).

- **Classes** show inheritance, descriptor and bytecode type references.
- **Fields** separate reads from writes.
- **Methods** separate callers from callees.

Results are grouped by the class they are in, with each member's parameter and return types and how it uses the
target. Double-click a result, or select it and press `Enter`, to open and highlight that member; double-click a
class heading to open the class. Tick **Follow the open class** to show the references of each class you open.

**Find Extenders**, **Find Implementors** and **Find Matching Class Nodes** in the same menu list their results here
too.

The reference index is rebuilt when a JAR changes, so queries don't scan every instruction again.
