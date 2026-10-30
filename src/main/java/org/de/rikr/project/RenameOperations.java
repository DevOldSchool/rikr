package org.de.rikr.project;

import org.de.rikr.mapping.format.NameMappings;
import org.de.rikr.model.OriginalNames;
import org.de.rikr.model.Workspace;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenamePreview;
import org.de.rikr.rename.RenameResult;
import org.de.rikr.rename.RenameService;
import org.de.rikr.rename.RenameType;
import org.de.rikr.rename.RenameUndoResult;
import org.de.rikr.symbol.Declaration;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
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
    // Counts renames and undos, so a save that started earlier can tell whether it still has the latest names.
    private long changeCount;

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

    public RenamePreview preview(String jar, Declaration declaration, String newName) {
        return switch (declaration) {
            case Declaration.ClassDeclaration target -> previewClass(jar, target.owner(), newName);
            case Declaration.FieldDeclaration target -> previewField(jar, target.owner(), target.field(), newName);
            case Declaration.MethodDeclaration target -> previewMethod(jar, target.owner(), target.method(), newName);
        };
    }

    public RenameResult rename(String jar, Declaration declaration, String newName) {
        return switch (declaration) {
            case Declaration.ClassDeclaration target -> renameClass(jar, target.owner(), newName);
            case Declaration.FieldDeclaration target -> renameField(jar, target.owner(), target.field(), newName);
            case Declaration.MethodDeclaration target -> renameMethod(jar, target.owner(), target.method(), newName);
        };
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

    /**
     * Every class, field and method of the JAR whose name differs from the name it was loaded with. A class is
     * listed when it or one of its members was renamed. Sorted by original class name.
     */
    public NameMappings nameMappings(String jar) {
        OriginalNames original = workspace.originalNames();
        List<NameMappings.ClassMapping> mappings = new ArrayList<>();
        for (ClassNode classNode : classes(jar)) {
            List<NameMappings.MemberMapping> fields = new ArrayList<>();
            for (FieldNode field : classNode.fields) {
                OriginalNames.Member loaded = original.field(field);
                if (!loaded.name().equals(field.name)) {
                    fields.add(new NameMappings.MemberMapping(loaded.name(), loaded.descriptor(), field.name));
                }
            }
            List<NameMappings.MemberMapping> methods = new ArrayList<>();
            for (MethodNode method : classNode.methods) {
                OriginalNames.Member loaded = original.method(method);
                if (!loaded.name().equals(method.name)) {
                    methods.add(new NameMappings.MemberMapping(loaded.name(), loaded.descriptor(), method.name));
                }
            }
            String originalName = original.className(classNode);
            if (!originalName.equals(classNode.name) || !fields.isEmpty() || !methods.isEmpty()) {
                mappings.add(new NameMappings.ClassMapping(originalName, classNode.name, fields, methods));
            }
        }
        mappings.sort(Comparator.comparing(NameMappings.ClassMapping::original));
        return new NameMappings(mappings);
    }

    public List<RenameOperation> history() {
        return service.getHistory();
    }

    public Set<String> dirtyJars() {
        return service.getDirtyJars();
    }

    public long changeCount() {
        return changeCount;
    }

    /**
     * Record that the JAR was exported. It stays unsaved if anything was renamed after {@code changeCountAtSave}.
     */
    public void markExported(String jar, long changeCountAtSave) {
        if (changeCount == changeCountAtSave) {
            service.markExported(jar);
        }
        events.publish(new ProjectEvent.JarsSaved());
    }

    /**
     * Record that the original JAR now contains the {@code written} renames, so they must not be replayed when the
     * project is reopened. It stays unsaved if anything was renamed after {@code changeCountAtSave}.
     */
    public void commitOverwrittenJar(String jar, List<RenameOperation> written, long changeCountAtSave) {
        service.commitOverwrittenJar(jar, written, changeCount == changeCountAtSave);
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
        changeCount++;
        events.publish(new ProjectEvent.Renamed(operation, undo, result.declarationOwners()));
    }
}
