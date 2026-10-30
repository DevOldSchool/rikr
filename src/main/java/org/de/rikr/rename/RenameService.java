package org.de.rikr.rename;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class RenameService {
    private final RenameEngine engine = new RenameEngine();
    private final List<RenameOperation> history = new ArrayList<>();
    private final Set<String> dirtyJars = new HashSet<>();

    public RenamePreview previewClassRename(List<ClassNode> classes, ClassNode owner, String newName) {
        Objects.requireNonNull(owner, "owner");
        String validation = validateClassRename(classes, owner, newName);
        if (validation != null) {
            return RenamePreview.invalid(RenameType.CLASS, owner.name, newName, validation);
        }
        RenameEngine.Changes changes = engine.renameClass(classes, owner.name, newName, false);
        return RenamePreview.valid(RenameType.CLASS, owner.name, newName, result("class", changes), changes.affectedClasses());
    }

    public RenamePreview previewFieldRename(
        List<ClassNode> classes,
        ClassNode owner,
        FieldNode field,
        String newName
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(field, "field");
        String validation = validateFieldRename(classes, owner, field, newName);
        if (validation != null) {
            return RenamePreview.invalid(RenameType.FIELD, field.name, newName, validation);
        }
        RenameEngine.Changes changes = engine.renameField(classes, owner.name, field.name, field.desc, newName, false);
        return RenamePreview.valid(RenameType.FIELD, field.name, newName, result("field", changes), changes.affectedClasses());
    }

    public RenamePreview previewMethodRename(
        List<ClassNode> classes,
        ClassNode owner,
        MethodNode method,
        String newName
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(method, "method");
        String validation = validateMethodRename(classes, owner, method, newName);
        if (validation != null) {
            return RenamePreview.invalid(RenameType.METHOD, method.name, newName, validation);
        }
        RenameEngine.Changes changes = engine.renameMethod(classes, owner.name, method.name, method.desc, newName, false);
        return RenamePreview.valid(RenameType.METHOD, method.name, newName, result("method", changes), changes.affectedClasses());
    }

    public synchronized RenameResult applyClassRename(
        String jarName,
        List<ClassNode> classes,
        ClassNode owner,
        String newName
    ) {
        Objects.requireNonNull(owner, "owner");
        requireValid(validateClassRename(classes, owner, newName));
        String oldName = owner.name;
        RenameResult result = result("class", engine.renameClass(classes, oldName, newName, true));
        record(new RenameOperation(RenameType.CLASS, jarName, oldName, null, oldName, newName));
        return result;
    }

    public synchronized RenameResult applyFieldRename(
        String jarName,
        List<ClassNode> classes,
        ClassNode owner,
        FieldNode field,
        String newName
    ) {
        requireValid(validateFieldRename(classes, owner, field, newName));
        String oldName = field.name;
        String descriptor = field.desc;
        RenameResult result = result("field", engine.renameField(classes, owner.name, oldName, descriptor, newName, true));
        record(new RenameOperation(RenameType.FIELD, jarName, owner.name, descriptor, oldName, newName));
        return result;
    }

    public synchronized RenameResult applyMethodRename(
        String jarName,
        List<ClassNode> classes,
        ClassNode owner,
        MethodNode method,
        String newName
    ) {
        requireValid(validateMethodRename(classes, owner, method, newName));
        String oldName = method.name;
        String descriptor = method.desc;
        RenameResult result = result("method", engine.renameMethod(classes, owner.name, oldName, descriptor, newName, true));
        record(new RenameOperation(RenameType.METHOD, jarName, owner.name, descriptor, oldName, newName));
        return result;
    }

    public synchronized Optional<RenameUndoResult> undoLast(Function<String, List<ClassNode>> classesProvider) {
        Objects.requireNonNull(classesProvider, "classesProvider");
        if (history.isEmpty()) {
            return Optional.empty();
        }
        RenameOperation operation = history.get(history.size() - 1);
        List<ClassNode> classes = classesProvider.apply(operation.jarName());
        if (classes == null) {
            throw new IllegalStateException("JAR is no longer loaded: " + operation.jarName());
        }

        ClassNode affectedClass;
        RenameResult result;
        if (operation.type() == RenameType.CLASS) {
            affectedClass = requireClass(classes, operation.newName());
            result = result("class", engine.renameClass(classes, operation.newName(), operation.oldName(), true));
        } else if (operation.type() == RenameType.FIELD) {
            affectedClass = requireClass(classes, operation.ownerName());
            requireField(affectedClass, operation.newName(), operation.descriptor());
            result = result("field", engine.renameField(
                classes, operation.ownerName(), operation.newName(), operation.descriptor(), operation.oldName(), true
            ));
        } else {
            affectedClass = requireClass(classes, operation.ownerName());
            requireMethod(affectedClass, operation.newName(), operation.descriptor());
            result = result("method", engine.renameMethod(
                classes, operation.ownerName(), operation.newName(), operation.descriptor(), operation.oldName(), true
            ));
        }

        history.remove(history.size() - 1);
        dirtyJars.add(operation.jarName());
        return Optional.of(new RenameUndoResult(operation, result, affectedClass));
    }

    public synchronized void replay(
        List<RenameOperation> operations,
        Function<String, List<ClassNode>> classesProvider
    ) {
        clear();
        for (RenameOperation operation : operations) {
            List<ClassNode> classes = classesProvider.apply(operation.jarName());
            if (classes == null) {
                throw new IllegalStateException("JAR is not loaded: " + operation.jarName());
            }
            applyPersistedOperation(operation, classes);
            history.add(operation);
            dirtyJars.add(operation.jarName());
        }
    }

    public synchronized List<RenameOperation> getHistory() {
        return List.copyOf(history);
    }

    public synchronized Set<String> getDirtyJars() {
        return Set.copyOf(dirtyJars);
    }

    public synchronized boolean canUndo() {
        return !history.isEmpty();
    }

    public synchronized void markExported(String jarName) {
        dirtyJars.remove(jarName);
    }

    /**
     * The original JAR was overwritten with the {@code written} renames applied, so they leave the history.
     *
     * @param clean true when nothing was renamed since the JAR was written
     */
    public synchronized void commitOverwrittenJar(String jarName, List<RenameOperation> written, boolean clean) {
        history.removeIf(operation -> operation.jarName().equals(jarName) && written.contains(operation));
        if (clean) {
            dirtyJars.remove(jarName);
        }
    }

    public synchronized void removeJar(String jarName) {
        history.removeIf(operation -> operation.jarName().equals(jarName));
        dirtyJars.remove(jarName);
    }

    public synchronized void clear() {
        history.clear();
        dirtyJars.clear();
    }

    private void applyPersistedOperation(RenameOperation operation, List<ClassNode> classes) {
        if (operation.type() == RenameType.CLASS) {
            requireValid(validateClassRename(classes, requireClass(classes, operation.oldName()), operation.newName()));
            engine.renameClass(classes, operation.oldName(), operation.newName(), true);
        } else if (operation.type() == RenameType.FIELD) {
            ClassNode owner = requireClass(classes, operation.ownerName());
            FieldNode field = requireField(owner, operation.oldName(), operation.descriptor());
            requireValid(validateFieldRename(classes, owner, field, operation.newName()));
            engine.renameField(classes, owner.name, field.name, field.desc, operation.newName(), true);
        } else {
            ClassNode owner = requireClass(classes, operation.ownerName());
            MethodNode method = requireMethod(owner, operation.oldName(), operation.descriptor());
            requireValid(validateMethodRename(classes, owner, method, operation.newName()));
            engine.renameMethod(classes, owner.name, method.name, method.desc, operation.newName(), true);
        }
    }

    private String validateClassRename(List<ClassNode> classes, ClassNode owner, String newName) {
        String common = validateName(owner.name, newName, true);
        if (common != null) {
            return common;
        }
        boolean collision = classes.stream()
            .anyMatch(classNode -> classNode != owner && classNode.name.equals(newName));
        return collision ? "A class named " + newName + " already exists in this JAR." : null;
    }

    private String validateFieldRename(List<ClassNode> classes, ClassNode owner, FieldNode field, String newName) {
        String common = validateName(field.name, newName, false);
        if (common != null) {
            return common;
        }
        boolean collision = owner.fields.stream()
            .anyMatch(candidate -> candidate != field && candidate.name.equals(newName));
        if (collision) {
            return "A field named " + newName + " already exists in " + owner.name + ".";
        }
        // A same-typed field in a related class would capture references that now resolve to this field.
        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        Set<String> related = new LinkedHashSet<>(hierarchy.selfAndAncestors(owner.name));
        related.addAll(hierarchy.selfAndDescendants(owner.name));
        for (String name : related) {
            ClassNode classNode = hierarchy.get(name);
            if (classNode != null && classNode != owner && ClassHierarchy.findField(classNode, newName, field.desc) != null) {
                return "A field named " + newName + " with the same type already exists in related class " + name + ".";
            }
        }
        return null;
    }

    private String validateMethodRename(List<ClassNode> classes, ClassNode owner, MethodNode method, String newName) {
        if (method.name.startsWith("<")) {
            return "Constructors and class initializers cannot be renamed.";
        }
        String common = validateName(method.name, newName, false);
        if (common != null) {
            return common;
        }
        if (newName.startsWith("<")) {
            return "Invalid JVM name: " + newName;
        }

        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        Set<String> family = engine.methodFamily(hierarchy, owner.name, method.name, method.desc);
        if (ClassHierarchy.isOverridable(method)) {
            for (String member : family) {
                for (String ancestor : hierarchy.selfAndAncestors(member)) {
                    if (!hierarchy.contains(ancestor)
                        && ExternalTypes.declaresOverridableMethod(ancestor, method.name, method.desc)) {
                        return method.name + " overrides a method of " + ancestor + ", which is outside this JAR.";
                    }
                }
            }
        }

        // The new name must not exist anywhere a renamed declaration is visible, or dispatch would change.
        Set<String> visibleFrom = new LinkedHashSet<>();
        for (String member : family) {
            visibleFrom.addAll(hierarchy.selfAndDescendants(member));
        }
        for (String className : visibleFrom) {
            for (String ancestor : hierarchy.selfAndAncestors(className)) {
                ClassNode classNode = hierarchy.get(ancestor);
                boolean declared = classNode == null
                    ? ExternalTypes.declaresOverridableMethod(ancestor, newName, method.desc)
                    : ClassHierarchy.findMethod(classNode, newName, method.desc) != null;
                if (declared) {
                    return "Renaming would collide with " + ancestor + "." + newName + method.desc + ".";
                }
            }
        }
        return null;
    }

    private String validateName(String oldName, String newName, boolean allowInternalClassName) {
        if (newName == null || newName.isBlank()) {
            return "The new name cannot be blank.";
        }
        if (!newName.equals(newName.trim())) {
            return "The new name cannot start or end with whitespace.";
        }
        if (oldName.equals(newName)) {
            return "The new name is unchanged.";
        }
        String[] parts = allowInternalClassName ? newName.split("/", -1) : new String[]{newName};
        for (String part : parts) {
            if (!isIdentifier(part)) {
                return "Invalid JVM name: " + newName;
            }
        }
        return null;
    }

    private boolean isIdentifier(String value) {
        if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (!Character.isJavaIdentifierPart(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private RenameResult result(String type, RenameEngine.Changes changes) {
        return new RenameResult(
            type,
            changes.referenceCount(),
            changes.affectedClasses().size(),
            changes.declarationOwners()
        );
    }

    private ClassNode requireClass(List<ClassNode> classes, String name) {
        return classes.stream()
            .filter(classNode -> classNode.name.equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find class " + name));
    }

    private FieldNode requireField(ClassNode owner, String name, String descriptor) {
        return owner.fields.stream()
            .filter(field -> field.name.equals(name) && Objects.equals(field.desc, descriptor))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find field " + owner.name + "." + name));
    }

    private MethodNode requireMethod(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
            .filter(method -> method.name.equals(name) && Objects.equals(method.desc, descriptor))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find method " + owner.name + "." + name));
    }

    private void requireValid(String validationMessage) {
        if (validationMessage != null) {
            throw new IllegalArgumentException(validationMessage);
        }
    }

    private void record(RenameOperation operation) {
        history.add(operation);
        dirtyJars.add(operation.jarName());
    }
}
