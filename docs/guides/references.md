# References and call hierarchy

Select a class, field or method in the project tree or put the caret on its name in the code, then choose **Code >
Show References** (`Alt+F7`) or **Show References** from the right-click menu. **View > References and Call
Hierarchy** (`Shift+Cmd+R`) opens the workspace.

- **Classes** show inheritance, descriptor and bytecode type references.
- **Fields** separate reads from writes.
- **Methods** separate callers from callees.

The results sit under the code. They are grouped by the class they are in, with each member's parameter and return
types and how it uses the target. Selecting a result shows and highlights it in the code above, so the arrow keys
step through them without leaving the list; `Enter` or a double-click also moves the caret into the code. Selecting
a class heading shows the class. Tick **Follow the open class** to show the references of each class you open.

**Find Extenders**, **Find Implementors** and **Find Similar Classes in Other JARs** in the tree's right-click menu
list their results here too.

The reference index is rebuilt when a JAR changes, so queries don't scan every instruction again.
