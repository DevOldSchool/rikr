package org.de.rikr.project;

import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.GeneratedNames;
import org.de.rikr.mapping.MemberMatch;
import org.de.rikr.mapping.MemberMatches;
import org.de.rikr.mapping.format.NameMappings;
import org.de.rikr.model.OriginalNames;
import org.de.rikr.model.Workspace;
import org.de.rikr.rename.RenameBatch;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenamePreview;
import org.de.rikr.rename.RenameResult;
import org.de.rikr.rename.RenameService;
import org.de.rikr.rename.RenameType;
import org.de.rikr.rename.RenameStep;
import org.de.rikr.rename.RenameUndoResult;
import org.de.rikr.symbol.Declaration;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

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

    /**
     * A mapping file checked against a JAR.
     *
     * @param skipped classes and members the file names that are not in the JAR, as it was loaded
     */
    public record ImportPlan(RenameService.BatchPlan plan, int skipped) {
        public RenameBatch batch() {
            return plan.batch();
        }

        public List<String> problems() {
            return plan.problems();
        }
    }

    /**
     * Match a mapping file to the JAR and check the renames it asks for. The file names classes and members as
     * they were loaded, so this works even after other renames. Entries that keep their original name, or already
     * have the new name, are left out.
     */
    public ImportPlan planImport(String jar, NameMappings mappings, String source) {
        OriginalNames original = workspace.originalNames();
        Map<String, ClassNode> byOriginalName = new HashMap<>();
        for (ClassNode classNode : classes(jar)) {
            byOriginalName.put(original.className(classNode), classNode);
        }
        Map<String, String> classRenames = new LinkedHashMap<>();
        List<RenameBatch.MemberRename> fields = new ArrayList<>();
        List<RenameBatch.MemberRename> methods = new ArrayList<>();
        int skipped = 0;
        for (NameMappings.ClassMapping mapping : mappings.classes()) {
            ClassNode classNode = byOriginalName.get(mapping.original());
            if (classNode == null) {
                skipped += 1 + mapping.fields().size() + mapping.methods().size();
                continue;
            }
            if (!mapping.named().equals(mapping.original()) && !mapping.named().equals(classNode.name)) {
                classRenames.put(classNode.name, mapping.named());
            }
            for (NameMappings.MemberMapping member : mapping.fields()) {
                FieldNode field = classNode.fields.stream()
                    .filter(candidate -> original.field(candidate).equals(
                        new OriginalNames.Member(member.original(), member.descriptor())))
                    .findFirst().orElse(null);
                if (field == null) {
                    skipped++;
                } else if (!member.named().equals(member.original()) && !member.named().equals(field.name)) {
                    fields.add(new RenameBatch.MemberRename(classNode.name, field.name, field.desc, member.named()));
                }
            }
            for (NameMappings.MemberMapping member : mapping.methods()) {
                MethodNode method = classNode.methods.stream()
                    .filter(candidate -> original.method(candidate).equals(
                        new OriginalNames.Member(member.original(), member.descriptor())))
                    .findFirst().orElse(null);
                if (method == null) {
                    skipped++;
                } else if (!member.named().equals(member.original()) && !member.named().equals(method.name)) {
                    methods.add(new RenameBatch.MemberRename(classNode.name, method.name, method.desc, member.named()));
                }
            }
        }
        RenameBatch requested = new RenameBatch(jar, source, classRenames, fields, methods);
        return new ImportPlan(service.planBatch(classes(jar), requested), skipped);
    }

    /**
     * Apply a mapping file checked by {@link #planImport}. Undo reverts the whole import.
     *
     * @throws IllegalArgumentException if the plan has problems or the JAR changed since it was made
     */
    public RenameResult applyImport(ImportPlan plan) {
        if (!plan.problems().isEmpty()) {
            throw new IllegalArgumentException(plan.problems().getFirst());
        }
        return applyBatch(plan.batch());
    }

    /**
     * Names from one JAR checked against another JAR whose classes and members were matched to them.
     *
     * @param plan         the renames, with those that can't be made left out
     * @param alreadyNamed matched classes and members of the target that were renamed already, so keep their names
     */
    public record NameTransfer(RenameService.PartialPlan plan, int alreadyNamed) {
        public RenameBatch batch() {
            return plan.plan().batch();
        }

        public List<String> leftOut() {
            return plan.leftOut();
        }
    }

    /**
     * Plan giving each matched class and member of the target JAR the name its match has in the source JAR. A name
     * onto a target that was renamed already is not carried, and renames that can't be made, such as two matches
     * carrying the same name into one class, are left out and said why.
     *
     * @param includeLoadedNames carry the names the source JAR was loaded with too, for a JAR that was named
     *                           before it was loaded; otherwise only names given since it was loaded are carried.
     *                           Loaded names a deobfuscator made up are left out, and so are obfuscated ones:
     *                           names of the short shape most of the source's names have, and names the target
     *                           JAR was loaded with too, since obfuscators draw names such as "ao" from the same
     *                           small set in every version, while names people choose rarely recur that way.
     */
    public NameTransfer planNameTransfer(
        String sourceJar,
        String targetJar,
        List<ClassMappingDecision> classMappings,
        MemberMatches members,
        boolean includeLoadedNames
    ) {
        OriginalNames original = workspace.originalNames();
        Map<String, ClassNode> sources = byName(classes(sourceJar));
        Map<String, ClassNode> targets = byName(classes(targetJar));
        Set<String> targetClassNames = new HashSet<>();
        Set<String> targetFieldNames = new HashSet<>();
        Set<String> targetMethodNames = new HashSet<>();
        for (ClassNode classNode : targets.values()) {
            String loaded = original.className(classNode);
            targetClassNames.add(loaded.substring(loaded.lastIndexOf('/') + 1));
            classNode.fields.forEach(field -> targetFieldNames.add(original.field(field).name()));
            classNode.methods.forEach(method -> targetMethodNames.add(original.method(method).name()));
        }
        List<String> sourceClassNames = new ArrayList<>();
        List<String> sourceMemberNames = new ArrayList<>();
        for (ClassNode classNode : sources.values()) {
            String loaded = original.className(classNode);
            sourceClassNames.add(loaded.substring(loaded.lastIndexOf('/') + 1));
            classNode.fields.forEach(field -> sourceMemberNames.add(original.field(field).name()));
            classNode.methods.forEach(method -> sourceMemberNames.add(original.method(method).name()));
        }
        Predicate<String> obfuscatedClass = GeneratedNames.obfuscatedIn(sourceClassNames);
        Predicate<String> obfuscatedMember = GeneratedNames.obfuscatedIn(sourceMemberNames);
        Predicate<String> classShared = name -> obfuscatedClass.test(name) || targetClassNames.contains(name);
        Predicate<String> fieldShared = name -> obfuscatedMember.test(name) || targetFieldNames.contains(name);
        Predicate<String> methodShared = name -> obfuscatedMember.test(name) || targetMethodNames.contains(name);
        Map<String, String> classRenames = new LinkedHashMap<>();
        int alreadyNamed = 0;
        for (ClassMappingDecision mapping : classMappings) {
            ClassNode source = sources.get(mapping.sourceClassName());
            ClassNode target = targets.get(mapping.targetClassName());
            if (source == null || target == null || target.name.equals(source.name)
                || !carries(original.className(source), source.name, includeLoadedNames, classShared)) {
                continue;
            }
            if (!original.className(target).equals(target.name)) {
                alreadyNamed++;
                continue;
            }
            classRenames.put(target.name, source.name);
        }
        List<RenameBatch.MemberRename> fields = new ArrayList<>();
        for (MemberMatch match : members.fields()) {
            FieldNode source = field(sources, match.source());
            FieldNode target = field(targets, match.target());
            if (source == null || target == null || target.name.equals(source.name)
                || !carries(original.field(source).name(), source.name, includeLoadedNames, fieldShared)) {
                continue;
            }
            if (!original.field(target).name().equals(target.name)) {
                alreadyNamed++;
                continue;
            }
            fields.add(new RenameBatch.MemberRename(match.target().owner(), target.name, target.desc, source.name));
        }
        List<RenameBatch.MemberRename> methods = new ArrayList<>();
        for (MemberMatch match : members.methods()) {
            MethodNode source = method(sources, match.source());
            MethodNode target = method(targets, match.target());
            if (source == null || target == null || target.name.equals(source.name)
                || !carries(original.method(source).name(), source.name, includeLoadedNames, methodShared)) {
                continue;
            }
            if (!original.method(target).name().equals(target.name)) {
                alreadyNamed++;
                continue;
            }
            methods.add(new RenameBatch.MemberRename(match.target().owner(), target.name, target.desc, source.name));
        }
        RenameBatch requested = new RenameBatch(targetJar, "names from " + sourceJar, classRenames, fields, methods);
        return new NameTransfer(service.planBatchLeavingOutProblems(classes(targetJar), requested), alreadyNamed);
    }

    private static boolean carries(String loadedName, String name, boolean includeLoadedNames,
        Predicate<String> obfuscated) {
        if (!loadedName.equals(name)) {
            return true;
        }
        return includeLoadedNames && !GeneratedNames.isGenerated(name)
            && !obfuscated.test(name.substring(name.lastIndexOf('/') + 1));
    }

    /**
     * Apply a batch of renames checked by {@link #planImport} or {@link #planNameTransfer}. Undo reverts all of it.
     *
     * @throws IllegalArgumentException if the batch no longer fits the JAR
     */
    public RenameResult applyBatch(RenameBatch batch) {
        RenameResult result = service.applyBatch(classes(batch.jarName()), batch);
        publish(batch, false, result);
        return result;
    }

    private static Map<String, ClassNode> byName(List<ClassNode> classes) {
        Map<String, ClassNode> byName = new HashMap<>();
        for (ClassNode classNode : classes) {
            byName.put(classNode.name, classNode);
        }
        return byName;
    }

    private static FieldNode field(Map<String, ClassNode> classes, MemberMatch.MemberRef ref) {
        ClassNode owner = classes.get(ref.owner());
        return owner == null ? null : owner.fields.stream()
            .filter(field -> field.name.equals(ref.name()) && field.desc.equals(ref.descriptor()))
            .findFirst().orElse(null);
    }

    private static MethodNode method(Map<String, ClassNode> classes, MemberMatch.MemberRef ref) {
        ClassNode owner = classes.get(ref.owner());
        return owner == null ? null : owner.methods.stream()
            .filter(method -> method.name.equals(ref.name()) && method.desc.equals(ref.descriptor()))
            .findFirst().orElse(null);
    }

    public Optional<RenameUndoResult> undoLast() {
        Optional<RenameUndoResult> undo = service.undoLast(workspace::classes);
        undo.ifPresent(value -> publish(value.step(), true, value.result()));
        return undo;
    }

    /**
     * Apply again the step undone last, unless something was renamed since.
     *
     * @throws IllegalArgumentException if the step no longer fits the JAR
     */
    public Optional<RenameUndoResult> redoLast() {
        Optional<RenameUndoResult> redo = service.redoLast(workspace::classes);
        redo.ifPresent(value -> publish(value.step(), false, value.result()));
        return redo;
    }

    public Optional<RenameStep> nextUndo() {
        return service.nextUndo();
    }

    public Optional<RenameStep> nextRedo() {
        return service.nextRedo();
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

    public List<RenameStep> history() {
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
    public void commitOverwrittenJar(String jar, List<RenameStep> written, long changeCountAtSave) {
        service.commitOverwrittenJar(jar, written, changeCount == changeCountAtSave);
        events.publish(new ProjectEvent.JarsSaved());
    }

    /**
     * Apply saved renames to freshly loaded JARs. No events are published: the saved mappings and bookmarks
     * already use the renamed names.
     *
     * @return the JARs that were renamed
     */
    Set<String> replay(List<RenameStep> steps) {
        service.replay(steps, workspace::classes);
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

    private void publish(RenameStep step, boolean undo, RenameResult result) {
        changeCount++;
        events.publish(new ProjectEvent.Renamed(step, undo, result.declarationOwners()));
    }
}
