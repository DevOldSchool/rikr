package org.de.rikr.project;

import org.de.rikr.model.Workspace;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenamePreview;
import org.de.rikr.rename.RenameResult;
import org.de.rikr.rename.RenameService;
import org.de.rikr.rename.RenameType;
import org.de.rikr.rename.RenameUndoResult;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Renaming classes and members, with undo and the record of which JARs have unsaved renames.
 */
public class RenameOperations {
    private final Workspace workspace;
    private final EventBus events;
    private final RenameService service = new RenameService();

    RenameOperations(Workspace workspace, EventBus events) {
        this.workspace = workspace;
        this.events = events;
    }

    public RenamePreview previewClass(String jar, ClassNode owner, String newName) {
        return service.previewClassRename(classes(jar), owner, newName);
    }

    public RenamePreview previewField(String jar, ClassNode owner, FieldNode field, String newName) {
        return service.previewFieldRename(classes(jar), owner, field, newName);
    }

    public RenamePreview previewMethod(String jar, ClassNode owner, MethodNode method, String newName) {
        return service.previewMethodRename(classes(jar), owner, method, newName);
    }

    public RenameResult renameClass(String jar, ClassNode owner, String newName) {
        String oldName = owner.name;
        RenameResult result = service.applyClassRename(jar, classes(jar), owner, newName);
        publish(new RenameOperation(RenameType.CLASS, jar, oldName, null, oldName, newName), false, result);
        return result;
    }

    public RenameResult renameField(String jar, ClassNode owner, FieldNode field, String newName) {
        String oldName = field.name;
        RenameResult result = service.applyFieldRename(jar, classes(jar), owner, field, newName);
        publish(new RenameOperation(RenameType.FIELD, jar, owner.name, field.desc, oldName, newName), false, result);
        return result;
    }

    public RenameResult renameMethod(String jar, ClassNode owner, MethodNode method, String newName) {
        String oldName = method.name;
        RenameResult result = service.applyMethodRename(jar, classes(jar), owner, method, newName);
        publish(new RenameOperation(RenameType.METHOD, jar, owner.name, method.desc, oldName, newName), false, result);
        return result;
    }

    public Optional<RenameUndoResult> undoLast() {
        Optional<RenameUndoResult> undo = service.undoLast(workspace::classes);
        undo.ifPresent(value -> publish(value.operation(), true, value.result()));
        return undo;
    }

    public List<RenameOperation> history() {
        return service.getHistory();
    }

    public Set<String> dirtyJars() {
        return service.getDirtyJars();
    }

    public void markExported(String jar) {
        service.markExported(jar);
        events.publish(new ProjectEvent.JarsSaved());
    }

    public void commitOverwrittenJar(String jar) {
        service.commitOverwrittenJar(jar);
        events.publish(new ProjectEvent.JarsSaved());
    }

    /**
     * Apply saved renames to freshly loaded JARs. No events are published: the saved mappings and bookmarks
     * already use the renamed names.
     *
     * @return the JARs that were renamed
     */
    Set<String> replay(List<RenameOperation> operations) {
        service.replay(operations, workspace::classes);
        return service.getDirtyJars();
    }

    void removeJar(String jar) {
        service.removeJar(jar);
    }

    private List<ClassNode> classes(String jar) {
        List<ClassNode> classes = workspace.classes(jar);
        if (classes == null) {
            throw new IllegalArgumentException("JAR is not loaded: " + jar);
        }
        return classes;
    }

    private void publish(RenameOperation operation, boolean undo, RenameResult result) {
        events.publish(new ProjectEvent.Renamed(operation, undo, result.declarationOwners()));
    }
}
