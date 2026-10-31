package org.de.rikr.rename;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class RenameService {
    private final RenameEngine engine = new RenameEngine();
    private final List<RenameStep> history = new ArrayList<>();
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

    /**
     * The result of checking a batch: the batch to apply, with every overridden method's family filled in, and
     * the problems that stop it from being applied.
     *
     * @param refusedClasses requested class renames, by old name, that caused a problem
     * @param refusedMembers requested field and method renames that caused a problem
     */
    public record BatchPlan(
        RenameBatch batch,
        List<String> problems,
        Set<String> refusedClasses,
        Set<RenameBatch.MemberRename> refusedMembers
    ) {
        public BatchPlan {
            problems = List.copyOf(problems);
            refusedClasses = Set.copyOf(refusedClasses);
            refusedMembers = Set.copyOf(refusedMembers);
        }

        public boolean valid() {
            return problems.isEmpty();
        }
    }

    /**
     * A batch with the renames that can't be made left out.
     *
     * @param leftOut why each left-out rename can't be made
     */
    public record PartialPlan(BatchPlan plan, List<String> leftOut) {
        public PartialPlan {
            leftOut = List.copyOf(leftOut);
        }
    }

    /**
     * Check a batch like {@link #planBatch}, leaving out each rename that causes a problem instead of refusing the
     * whole batch. Leaving one out can resolve another's problem, such as two names colliding, so the rest is
     * checked again until it is valid.
     */
    public PartialPlan planBatchLeavingOutProblems(List<ClassNode> classes, RenameBatch requested) {
        List<String> leftOut = new ArrayList<>();
        RenameBatch remaining = requested;
        while (true) {
            BatchPlan plan = planBatch(classes, remaining);
            if (plan.valid() || plan.refusedClasses().isEmpty() && plan.refusedMembers().isEmpty()) {
                return new PartialPlan(plan, leftOut);
            }
            leftOut.addAll(plan.problems());
            Map<String, String> classNames = new LinkedHashMap<>(remaining.classes());
            classNames.keySet().removeAll(plan.refusedClasses());
            remaining = new RenameBatch(remaining.jarName(), remaining.source(), classNames,
                remaining.fields().stream().filter(field -> !plan.refusedMembers().contains(field)).toList(),
                remaining.methods().stream().filter(method -> !plan.refusedMembers().contains(method)).toList());
        }
    }

    /**
     * Check a batch of renames against the JAR. Each method rename is extended to every declaration in its
     * family. Two classes, or two members of one class, may not end up with the same name, and a renamed field
     * or method may not start to hide or override another one.
     */
    public BatchPlan planBatch(List<ClassNode> classes, RenameBatch requested) {
        List<String> problems = new ArrayList<>();
        Set<String> refusedClasses = new HashSet<>();
        Set<RenameBatch.MemberRename> refusedMembers = new HashSet<>();
        ClassHierarchy hierarchy = new ClassHierarchy(classes);

        Map<String, String> classNames = new LinkedHashMap<>();
        requested.classes().forEach((oldName, newName) -> {
            if (!hierarchy.contains(oldName)) {
                problems.add("Class " + oldName + " is not in the JAR.");
                refusedClasses.add(oldName);
                return;
            }
            String invalid = validateName(oldName, newName, true);
            if (invalid != null) {
                problems.add(oldName + ": " + invalid);
                refusedClasses.add(oldName);
                return;
            }
            classNames.put(oldName, newName);
        });
        Map<String, String> finalClassNames = new HashMap<>();
        for (ClassNode classNode : classes) {
            String finalName = classNames.getOrDefault(classNode.name, classNode.name);
            String other = finalClassNames.putIfAbsent(finalName, classNode.name);
            if (other != null) {
                problems.add("Classes " + other + " and " + classNode.name + " would both be named " + finalName + ".");
                for (String className : List.of(other, classNode.name)) {
                    if (classNames.containsKey(className)) {
                        refusedClasses.add(className);
                    }
                }
            }
        }

        Map<String, String> fieldNames = new LinkedHashMap<>();
        List<RenameBatch.MemberRename> fields = new ArrayList<>();
        for (RenameBatch.MemberRename field : requested.fields()) {
            ClassNode owner = hierarchy.get(field.owner());
            if (owner == null || ClassHierarchy.findField(owner, field.name(), field.descriptor()) == null) {
                problems.add("Field " + field.owner() + "." + field.name() + " is not in the JAR.");
                refusedMembers.add(field);
                continue;
            }
            String invalid = validateName(field.name(), field.newName(), false);
            if (invalid != null) {
                problems.add(field.owner() + "." + field.name() + ": " + invalid);
                refusedMembers.add(field);
                continue;
            }
            fieldNames.put(memberKey(field.owner(), field.name(), field.descriptor()), field.newName());
            fields.add(field);
        }
        for (RenameBatch.MemberRename field : fields) {
            Set<String> related = new LinkedHashSet<>(hierarchy.selfAndAncestors(field.owner()));
            related.addAll(hierarchy.selfAndDescendants(field.owner()));
            for (String className : related) {
                ClassNode classNode = hierarchy.get(className);
                if (classNode == null) {
                    continue;
                }
                boolean sameClass = className.equals(field.owner());
                for (FieldNode other : classNode.fields) {
                    // Fields that already shared the name keep the relation they had; only a new match matters.
                    if (other.name.equals(field.name())) {
                        continue;
                    }
                    // In its own class a field name must be unique; in related classes only a same-typed field
                    // would capture references.
                    String otherName = fieldNames.getOrDefault(memberKey(className, other.name, other.desc), other.name);
                    if (otherName.equals(field.newName()) && (sameClass || other.desc.equals(field.descriptor()))) {
                        problems.add("Field " + field.owner() + "." + field.name() + " would be named " + field.newName()
                            + ", like the field in " + className + ".");
                    refusedMembers.add(field);
                    }
                }
            }
        }

        Map<String, String> methodNames = new LinkedHashMap<>();
        Map<String, Set<String>> allFamilies = requested.methods().isEmpty() ? Map.of() : engine.methodFamilies(hierarchy);
        List<Set<String>> families = new ArrayList<>();
        // Families are shared sets, so each is checked for collisions once however many of its methods are listed.
        Set<Set<String>> checkedFamilies = Collections.newSetFromMap(new IdentityHashMap<>());
        List<RenameBatch.MemberRename> familyRequests = new ArrayList<>();
        for (RenameBatch.MemberRename method : requested.methods()) {
            ClassNode owner = hierarchy.get(method.owner());
            MethodNode node = owner == null ? null : ClassHierarchy.findMethod(owner, method.name(), method.descriptor());
            if (node == null) {
                problems.add("Method " + method.owner() + "." + method.name() + method.descriptor() + " is not in the JAR.");
                refusedMembers.add(method);
                continue;
            }
            String label = method.owner() + "." + method.name() + method.descriptor();
            if (method.name().startsWith("<") || method.newName().startsWith("<")) {
                problems.add(label + ": constructors and class initializers cannot be renamed.");
                refusedMembers.add(method);
                continue;
            }
            String invalid = validateName(method.name(), method.newName(), false);
            if (invalid != null) {
                problems.add(label + ": " + invalid);
                refusedMembers.add(method);
                continue;
            }
            Set<String> family = allFamilies.get(memberKey(method.owner(), method.name(), method.descriptor()));
            String outside = externalOverride(hierarchy, family, node);
            if (outside != null) {
                problems.add(label + " overrides a method of " + outside + ", which is outside this JAR.");
                refusedMembers.add(method);
                continue;
            }
            String conflict = null;
            for (String member : family) {
                String previous = methodNames.putIfAbsent(memberKey(member, method.name(), method.descriptor()), method.newName());
                if (previous != null && !previous.equals(method.newName())) {
                    conflict = previous;
                }
            }
            if (conflict != null) {
                problems.add(label + " is renamed to both " + conflict + " and " + method.newName()
                    + " through the methods it overrides.");
                refusedMembers.add(method);
            }
            if (conflict == null && checkedFamilies.add(family)) {
                families.add(family);
                familyRequests.add(method);
            }
        }
        for (int index = 0; index < families.size(); index++) {
            Set<String> family = families.get(index);
            RenameBatch.MemberRename method = familyRequests.get(index);
            String collision = methodCollision(hierarchy, family, method, methodNames);
            if (collision != null) {
                problems.add(collision);
                refusedMembers.add(method);
            }
        }
        List<RenameBatch.MemberRename> methods = new ArrayList<>();
        methodNames.forEach((key, newName) -> {
            int dot = key.indexOf('.');
            int descriptor = key.indexOf('(', dot);
            methods.add(new RenameBatch.MemberRename(
                key.substring(0, dot), key.substring(dot + 1, descriptor), key.substring(descriptor), newName));
        });

        RenameBatch batch = new RenameBatch(requested.jarName(), requested.source(), classNames, fields, methods);
        return new BatchPlan(batch, problems, refusedClasses, refusedMembers);
    }

    /**
     * Apply a batch checked by {@link #planBatch}.
     *
     * @throws IllegalArgumentException if the batch no longer fits the JAR
     */
    public synchronized RenameResult applyBatch(List<ClassNode> classes, RenameBatch batch) {
        BatchPlan plan = planBatch(classes, batch);
        if (!plan.valid()) {
            throw new IllegalArgumentException(plan.problems().getFirst());
        }
        RenameResult result = result("mapping", engine.renameBatch(classes, plan.batch(), true));
        record(plan.batch());
        return result;
    }

    public synchronized Optional<RenameUndoResult> undoLast(Function<String, List<ClassNode>> classesProvider) {
        Objects.requireNonNull(classesProvider, "classesProvider");
        if (history.isEmpty()) {
            return Optional.empty();
        }
        RenameStep step = history.get(history.size() - 1);
        List<ClassNode> classes = classesProvider.apply(step.jarName());
        if (classes == null) {
            throw new IllegalStateException("JAR is no longer loaded: " + step.jarName());
        }
        if (step instanceof RenameBatch batch) {
            RenameResult result = result("mapping", engine.renameBatch(classes, batch.inverse(), true));
            history.remove(history.size() - 1);
            dirtyJars.add(batch.jarName());
            return Optional.of(new RenameUndoResult(batch, result, null));
        }
        RenameOperation operation = (RenameOperation) step;

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
        List<RenameStep> steps,
        Function<String, List<ClassNode>> classesProvider
    ) {
        clear();
        for (RenameStep step : steps) {
            List<ClassNode> classes = classesProvider.apply(step.jarName());
            if (classes == null) {
                throw new IllegalStateException("JAR is not loaded: " + step.jarName());
            }
            switch (step) {
                case RenameOperation operation -> {
                    applyPersistedOperation(operation, classes);
                    record(operation);
                }
                case RenameBatch batch -> applyBatch(classes, batch);
            }
        }
    }

    public synchronized List<RenameStep> getHistory() {
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
    public synchronized void commitOverwrittenJar(String jarName, List<RenameStep> written, boolean clean) {
        history.removeIf(step -> step.jarName().equals(jarName) && written.contains(step));
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

    private void record(RenameStep step) {
        history.add(step);
        dirtyJars.add(step.jarName());
    }

    private static String memberKey(String owner, String name, String descriptor) {
        return owner + "." + name + descriptor;
    }

    /**
     * The class outside the JAR whose method a family member overrides, or null. Such a method keeps its name.
     */
    private static String externalOverride(ClassHierarchy hierarchy, Set<String> family, MethodNode method) {
        if (!ClassHierarchy.isOverridable(method)) {
            return null;
        }
        for (String member : family) {
            for (String ancestor : hierarchy.selfAndAncestors(member)) {
                if (!hierarchy.contains(ancestor)
                    && ExternalTypes.declaresOverridableMethod(ancestor, method.name, method.desc)) {
                    return ancestor;
                }
            }
        }
        return null;
    }

    /**
     * Why renaming the family would make it collide with another method, using the names after the batch, or null.
     */
    private static String methodCollision(
        ClassHierarchy hierarchy,
        Set<String> family,
        RenameBatch.MemberRename method,
        Map<String, String> methodNames
    ) {
        Set<String> visibleFrom = new LinkedHashSet<>();
        for (String member : family) {
            visibleFrom.addAll(hierarchy.selfAndDescendants(member));
        }
        Set<String> checked = new HashSet<>();
        for (String className : visibleFrom) {
            for (String ancestor : hierarchy.selfAndAncestors(className)) {
                if (!checked.add(ancestor)) {
                    continue;
                }
                ClassNode classNode = hierarchy.get(ancestor);
                if (classNode == null) {
                    if (ExternalTypes.declaresOverridableMethod(ancestor, method.newName(), method.descriptor())) {
                        return method.owner() + "." + method.name() + " would be named " + method.newName()
                            + ", which overrides " + ancestor + "." + method.newName() + method.descriptor() + ".";
                    }
                    continue;
                }
                for (MethodNode other : classNode.methods) {
                    // A method that already shared the name, in this family or not, keeps the relation it had.
                    if (other.name.equals(method.name()) || !other.desc.equals(method.descriptor())) {
                        continue;
                    }
                    String otherName = methodNames.getOrDefault(memberKey(ancestor, other.name, other.desc), other.name);
                    if (otherName.equals(method.newName())) {
                        return method.owner() + "." + method.name() + method.descriptor() + " would be named "
                            + method.newName() + ", like " + ancestor + "." + other.name + other.desc + ".";
                    }
                }
            }
        }
        return null;
    }
}
