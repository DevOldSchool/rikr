package org.de.rikr.service;

import org.de.rikr.Renamer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class RenameService {
    private final List<RenameOperation> history = new ArrayList<>();
    private final Set<String> dirtyJars = new HashSet<>();

    public RenameResult renameClassReferences(List<ClassNode> classes, String oldName, String newName) {
        return Renamer.updateClassReferences(classes, oldName, newName);
    }

    public RenameResult renameMethodReferences(
        List<ClassNode> classes,
        ClassNode owner,
        String oldName,
        String newName
    ) {
        return Renamer.updateMethodReferences(classes, owner, oldName, newName);
    }

    public RenameResult renameFieldReferences(
        List<ClassNode> classes,
        ClassNode owner,
        String oldName,
        String newName
    ) {
        return Renamer.updateFieldReferences(classes, owner, oldName, newName);
    }

    public RenamePreview previewClassRename(List<ClassNode> classes, ClassNode owner, String newName) {
        Objects.requireNonNull(owner, "owner");
        String validation = validateClassRename(classes, owner, newName);
        if (validation != null) {
            return RenamePreview.invalid(RenameType.CLASS, owner.name, newName, validation);
        }
        List<ClassNode> copies = copyClasses(classes);
        List<String> classNames = copies.stream().map(classNode -> classNode.name).toList();
        List<byte[]> before = classBytes(copies);
        RenameResult result = Renamer.updateClassReferences(copies, owner.name, newName);
        return RenamePreview.valid(
            RenameType.CLASS,
            owner.name,
            newName,
            result,
            affectedClasses(copies, classNames, before)
        );
    }

    public RenamePreview previewFieldRename(
        List<ClassNode> classes,
        ClassNode owner,
        FieldNode field,
        String newName
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(field, "field");
        String validation = validateFieldRename(owner, field, newName);
        if (validation != null) {
            return RenamePreview.invalid(RenameType.FIELD, field.name, newName, validation);
        }
        List<ClassNode> copies = copyClasses(classes);
        List<String> classNames = copies.stream().map(classNode -> classNode.name).toList();
        List<byte[]> before = classBytes(copies);
        ClassNode ownerCopy = findClass(copies, owner.name);
        RenameResult result = Renamer.updateFieldReferences(copies, ownerCopy, field.name, newName);
        return RenamePreview.valid(
            RenameType.FIELD,
            field.name,
            newName,
            result,
            affectedClasses(copies, classNames, before)
        );
    }

    public RenamePreview previewMethodRename(
        List<ClassNode> classes,
        ClassNode owner,
        MethodNode method,
        String newName
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(method, "method");
        String validation = validateMethodRename(owner, method, newName);
        if (validation != null) {
            return RenamePreview.invalid(RenameType.METHOD, method.name, newName, validation);
        }
        List<ClassNode> copies = copyClasses(classes);
        List<String> classNames = copies.stream().map(classNode -> classNode.name).toList();
        List<byte[]> before = classBytes(copies);
        ClassNode ownerCopy = findClass(copies, owner.name);
        RenameResult result = Renamer.updateMethodReferences(copies, ownerCopy, method.name, newName);
        return RenamePreview.valid(
            RenameType.METHOD,
            method.name,
            newName,
            result,
            affectedClasses(copies, classNames, before)
        );
    }

    public synchronized RenameResult applyClassRename(
        String jarName,
        List<ClassNode> classes,
        ClassNode owner,
        String newName
    ) {
        RenamePreview preview = previewClassRename(classes, owner, newName);
        requireValid(preview);
        String oldName = owner.name;
        RenameResult result = Renamer.updateClassReferences(classes, oldName, newName);
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
        RenamePreview preview = previewFieldRename(classes, owner, field, newName);
        requireValid(preview);
        String oldName = field.name;
        RenameResult result = Renamer.updateFieldReferences(classes, owner, oldName, newName);
        record(new RenameOperation(RenameType.FIELD, jarName, owner.name, field.desc, oldName, newName));
        return result;
    }

    public synchronized RenameResult applyMethodRename(
        String jarName,
        List<ClassNode> classes,
        ClassNode owner,
        MethodNode method,
        String newName
    ) {
        RenamePreview preview = previewMethodRename(classes, owner, method, newName);
        requireValid(preview);
        String oldName = method.name;
        RenameResult result = Renamer.updateMethodReferences(classes, owner, oldName, newName);
        record(new RenameOperation(RenameType.METHOD, jarName, owner.name, method.desc, oldName, newName));
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
            result = Renamer.updateClassReferences(classes, operation.newName(), operation.oldName());
        } else if (operation.type() == RenameType.FIELD) {
            affectedClass = requireClass(classes, operation.ownerName());
            result = Renamer.updateFieldReferences(classes, affectedClass, operation.newName(), operation.oldName());
        } else {
            affectedClass = requireClass(classes, operation.ownerName());
            result = Renamer.updateMethodReferences(classes, affectedClass, operation.newName(), operation.oldName());
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

    public synchronized void commitOverwrittenJar(String jarName) {
        history.removeIf(operation -> operation.jarName().equals(jarName));
        dirtyJars.remove(jarName);
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
            ClassNode owner = requireClass(classes, operation.oldName());
            Renamer.updateClassReferences(classes, owner.name, operation.newName());
        } else if (operation.type() == RenameType.FIELD) {
            ClassNode owner = requireClass(classes, operation.ownerName());
            requireField(owner, operation.oldName(), operation.descriptor());
            Renamer.updateFieldReferences(classes, owner, operation.oldName(), operation.newName());
        } else {
            ClassNode owner = requireClass(classes, operation.ownerName());
            requireMethod(owner, operation.oldName(), operation.descriptor());
            Renamer.updateMethodReferences(classes, owner, operation.oldName(), operation.newName());
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

    private String validateFieldRename(ClassNode owner, FieldNode field, String newName) {
        String common = validateName(field.name, newName, false);
        if (common != null) {
            return common;
        }
        boolean collision = owner.fields.stream()
            .anyMatch(candidate -> candidate != field && candidate.name.equals(newName));
        return collision ? "A field named " + newName + " already exists in " + owner.name + "." : null;
    }

    private String validateMethodRename(ClassNode owner, MethodNode method, String newName) {
        if (method.name.startsWith("<")) {
            return "Constructors and class initializers cannot be renamed.";
        }
        String common = validateName(method.name, newName, false);
        if (common != null) {
            return common;
        }
        Set<String> renamedDescriptors = owner.methods.stream()
            .filter(candidate -> candidate.name.equals(method.name))
            .map(candidate -> candidate.desc)
            .collect(java.util.stream.Collectors.toSet());
        boolean collision = owner.methods.stream()
            .anyMatch(candidate -> candidate.name.equals(newName)
                && renamedDescriptors.contains(candidate.desc));
        return collision
            ? "Renaming this method family would collide with " + newName + " in " + owner.name + "."
            : null;
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

    private List<ClassNode> copyClasses(List<ClassNode> classes) {
        List<ClassNode> copies = new ArrayList<>(classes.size());
        for (ClassNode classNode : classes) {
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            ClassNode copy = new ClassNode();
            new ClassReader(writer.toByteArray()).accept(copy, 0);
            copies.add(copy);
        }
        return copies;
    }

    private List<byte[]> classBytes(List<ClassNode> classes) {
        return classes.stream().map(this::classBytes).toList();
    }

    private byte[] classBytes(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private List<String> affectedClasses(
        List<ClassNode> classes,
        List<String> originalNames,
        List<byte[]> before
    ) {
        List<String> affected = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            if (!Arrays.equals(before.get(index), classBytes(classes.get(index)))) {
                affected.add(originalNames.get(index));
            }
        }
        return List.copyOf(affected);
    }

    private ClassNode findClass(List<ClassNode> classes, String name) {
        return classes.stream().filter(classNode -> classNode.name.equals(name)).findFirst().orElse(null);
    }

    private ClassNode requireClass(List<ClassNode> classes, String name) {
        ClassNode classNode = findClass(classes, name);
        if (classNode == null) {
            throw new IllegalStateException("Unable to find class " + name);
        }
        return classNode;
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

    private void requireValid(RenamePreview preview) {
        if (!preview.valid()) {
            throw new IllegalArgumentException(preview.validationMessage());
        }
    }

    private void record(RenameOperation operation) {
        history.add(operation);
        dirtyJars.add(operation.jarName());
    }
}
