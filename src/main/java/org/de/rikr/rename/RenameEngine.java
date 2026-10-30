package org.de.rikr.rename;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renames classes, fields and methods across a JAR using ASM's {@link ClassRemapper}, so every place a name
 * can appear (descriptors, signatures, frames, constants, handles, inner class attributes) is covered.
 * <p>
 * Member renames follow JVM resolution: a reference is renamed when it resolves to the renamed declaration,
 * and a virtual method is renamed together with every declaration that overrides it or that it overrides.
 * <p>
 * Remapping produces new tree nodes; changes are copied back into the original {@link ClassNode},
 * {@link FieldNode} and {@link MethodNode} objects so the UI's references stay valid. Instruction lists of
 * changed methods are replaced, so indexes that hold instruction nodes must be rebuilt after a rename.
 */
public final class RenameEngine {

    public record Changes(int referenceCount, List<String> affectedClasses, Set<String> declarationOwners) {
        public Changes {
            affectedClasses = List.copyOf(affectedClasses);
            declarationOwners = Set.copyOf(declarationOwners);
        }
    }

    public Changes renameClass(List<ClassNode> classes, String oldName, String newName, boolean apply) {
        CountingRemapper remapper = new CountingRemapper() {
            @Override
            public String map(String internalName) {
                return internalName.equals(oldName) ? hit(newName) : internalName;
            }
        };
        return remap(classes, remapper, Set.of(oldName), apply);
    }

    public Changes renameField(
        List<ClassNode> classes,
        String owner,
        String name,
        String descriptor,
        String newName,
        boolean apply
    ) {
        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        CountingRemapper remapper = new CountingRemapper() {
            @Override
            public String mapFieldName(String fieldOwner, String fieldName, String fieldDescriptor) {
                if (fieldName.equals(name)
                    && fieldDescriptor.equals(descriptor)
                    && owner.equals(hierarchy.resolveField(fieldOwner, fieldName, fieldDescriptor))) {
                    return hit(newName);
                }
                return fieldName;
            }
        };
        return remap(classes, remapper, Set.of(owner), apply);
    }

    public Changes renameMethod(
        List<ClassNode> classes,
        String owner,
        String name,
        String descriptor,
        String newName,
        boolean apply
    ) {
        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        Set<String> family = methodFamily(hierarchy, owner, name, descriptor);
        CountingRemapper remapper = new CountingRemapper() {
            @Override
            public String mapMethodName(String methodOwner, String methodName, String methodDescriptor) {
                if (methodName.equals(name)
                    && methodDescriptor.equals(descriptor)
                    && family.contains(hierarchy.resolveMethod(methodOwner, methodName, methodDescriptor))) {
                    return hit(newName);
                }
                return methodName;
            }

            @Override
            public String mapInvokeDynamicMethodName(String methodName, String methodDescriptor) {
                // A lambda's invokedynamic name is the functional interface method it implements.
                Type produced = Type.getReturnType(methodDescriptor);
                if (methodName.equals(name) && produced.getSort() == Type.OBJECT) {
                    for (String type : hierarchy.selfAndAncestors(produced.getInternalName())) {
                        if (family.contains(type)) {
                            return hit(newName);
                        }
                    }
                }
                return methodName;
            }
        };
        return remap(classes, remapper, family, apply);
    }

    /**
     * Apply every rename of the batch in one pass over the classes. Field and method references are matched to
     * the declaration they resolve to, so the batch must list each declaration it renames, including every
     * declaration of an overridden method's family.
     */
    public Changes renameBatch(List<ClassNode> classes, RenameBatch batch, boolean apply) {
        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        Map<String, String> fields = new HashMap<>();
        for (RenameBatch.MemberRename field : batch.fields()) {
            fields.put(field.owner() + "." + field.name() + ":" + field.descriptor(), field.newName());
        }
        Map<String, String> methods = new HashMap<>();
        // For lambdas, whose invokedynamic only names the interface method, not its descriptor.
        Map<String, String> methodsByName = new HashMap<>();
        for (RenameBatch.MemberRename method : batch.methods()) {
            methods.put(method.owner() + "." + method.name() + method.descriptor(), method.newName());
            methodsByName.put(method.owner() + "." + method.name(), method.newName());
        }
        Set<String> declarationOwners = new LinkedHashSet<>(batch.classes().keySet());
        batch.fields().forEach(field -> declarationOwners.add(field.owner()));
        batch.methods().forEach(method -> declarationOwners.add(method.owner()));

        CountingRemapper remapper = new CountingRemapper() {
            @Override
            public String map(String internalName) {
                String newName = batch.classes().get(internalName);
                return newName == null ? internalName : hit(newName);
            }

            @Override
            public String mapFieldName(String fieldOwner, String fieldName, String fieldDescriptor) {
                String declaring = hierarchy.resolveField(fieldOwner, fieldName, fieldDescriptor);
                String newName = declaring == null ? null : fields.get(declaring + "." + fieldName + ":" + fieldDescriptor);
                return newName == null ? fieldName : hit(newName);
            }

            @Override
            public String mapMethodName(String methodOwner, String methodName, String methodDescriptor) {
                String declaring = hierarchy.resolveMethod(methodOwner, methodName, methodDescriptor);
                String newName = declaring == null ? null : methods.get(declaring + "." + methodName + methodDescriptor);
                return newName == null ? methodName : hit(newName);
            }

            @Override
            public String mapInvokeDynamicMethodName(String methodName, String methodDescriptor) {
                Type produced = Type.getReturnType(methodDescriptor);
                if (produced.getSort() == Type.OBJECT) {
                    for (String type : hierarchy.selfAndAncestors(produced.getInternalName())) {
                        String newName = methodsByName.get(type + "." + methodName);
                        if (newName != null) {
                            return hit(newName);
                        }
                    }
                }
                return methodName;
            }
        };
        return remap(classes, remapper, declarationOwners, apply);
    }

    /**
     * The family of every method declared in the JAR, keyed by {@code owner + "." + name + descriptor}: the same
     * as {@link #methodFamily} for each method, found in one pass over the classes.
     */
    public Map<String, Set<String>> methodFamilies(ClassHierarchy hierarchy) {
        Map<String, String> parents = new HashMap<>();
        for (ClassNode classNode : hierarchy.classes()) {
            for (MethodNode method : classNode.methods) {
                String key = classNode.name + "." + method.name + method.desc;
                parents.put(key, key);
            }
        }
        // Every class links the overridable declarations it can see that share a name and descriptor.
        for (ClassNode classNode : hierarchy.classes()) {
            Map<String, String> firstDeclaration = new HashMap<>();
            for (String ancestor : hierarchy.selfAndAncestors(classNode.name)) {
                ClassNode ancestorNode = hierarchy.get(ancestor);
                if (ancestorNode == null) {
                    continue;
                }
                for (MethodNode method : ancestorNode.methods) {
                    if (!ClassHierarchy.isOverridable(method)) {
                        continue;
                    }
                    String signature = method.name + method.desc;
                    String key = ancestor + "." + signature;
                    String first = firstDeclaration.putIfAbsent(signature, key);
                    if (first != null) {
                        union(parents, first, key);
                    }
                }
            }
        }
        Map<String, Set<String>> byRoot = new HashMap<>();
        Map<String, Set<String>> families = new HashMap<>();
        for (String key : parents.keySet()) {
            Set<String> family = byRoot.computeIfAbsent(find(parents, key), root -> new LinkedHashSet<>());
            family.add(key.substring(0, key.lastIndexOf('.', key.indexOf('('))));
            families.put(key, family);
        }
        return families;
    }

    /**
     * Every class in the JAR whose declaration of {@code name + descriptor} must be renamed together with the
     * declaration in {@code owner}. Private, static and special methods are not overridden and form a family
     * of one.
     */
    public Set<String> methodFamily(ClassHierarchy hierarchy, String owner, String name, String descriptor) {
        ClassNode ownerNode = hierarchy.get(owner);
        MethodNode start = ownerNode == null ? null : ClassHierarchy.findMethod(ownerNode, name, descriptor);
        if (start == null) {
            throw new IllegalStateException("Unable to find method " + owner + "." + name + descriptor);
        }
        if (!ClassHierarchy.isOverridable(start)) {
            return Set.of(owner);
        }

        // Every class links all overridable declarations it can see; linked declarations share one name.
        Map<String, String> parents = new HashMap<>();
        parents.put(owner, owner);
        for (ClassNode classNode : hierarchy.classes()) {
            String first = null;
            for (String ancestor : hierarchy.selfAndAncestors(classNode.name)) {
                ClassNode ancestorNode = hierarchy.get(ancestor);
                MethodNode declared = ancestorNode == null
                    ? null
                    : ClassHierarchy.findMethod(ancestorNode, name, descriptor);
                if (declared == null || !ClassHierarchy.isOverridable(declared)) {
                    continue;
                }
                parents.putIfAbsent(ancestor, ancestor);
                if (first == null) {
                    first = ancestor;
                } else {
                    union(parents, first, ancestor);
                }
            }
        }

        String root = find(parents, owner);
        Set<String> family = new LinkedHashSet<>();
        for (String member : parents.keySet()) {
            if (find(parents, member).equals(root)) {
                family.add(member);
            }
        }
        return family;
    }

    private Changes remap(List<ClassNode> classes, CountingRemapper remapper, Set<String> declarationOwners, boolean apply) {
        List<String> affected = new ArrayList<>();
        List<ClassNode[]> pendingCopies = new ArrayList<>();
        for (ClassNode classNode : classes) {
            int before = remapper.hits;
            ClassNode remapped = new ClassNode(Opcodes.ASM9);
            classNode.accept(new ClassRemapper(remapped, remapper));
            if (remapper.hits != before) {
                affected.add(classNode.name);
                pendingCopies.add(new ClassNode[]{classNode, remapped});
            }
        }
        // Copy only after every class is remapped: resolution reads the hierarchy as it was before the rename.
        if (apply) {
            for (ClassNode[] pair : pendingCopies) {
                copyInto(pair[0], pair[1]);
            }
        }
        return new Changes(remapper.hits, affected, declarationOwners);
    }

    private static void copyInto(ClassNode target, ClassNode source) {
        if (target.fields.size() != source.fields.size() || target.methods.size() != source.methods.size()) {
            throw new IllegalStateException("Remapping changed the member count of " + target.name);
        }
        target.version = source.version;
        target.access = source.access;
        target.name = source.name;
        target.signature = source.signature;
        target.superName = source.superName;
        target.interfaces = source.interfaces;
        target.sourceFile = source.sourceFile;
        target.sourceDebug = source.sourceDebug;
        target.module = source.module;
        target.outerClass = source.outerClass;
        target.outerMethod = source.outerMethod;
        target.outerMethodDesc = source.outerMethodDesc;
        target.visibleAnnotations = source.visibleAnnotations;
        target.invisibleAnnotations = source.invisibleAnnotations;
        target.visibleTypeAnnotations = source.visibleTypeAnnotations;
        target.invisibleTypeAnnotations = source.invisibleTypeAnnotations;
        target.attrs = source.attrs;
        target.innerClasses = source.innerClasses;
        target.nestHostClass = source.nestHostClass;
        target.nestMembers = source.nestMembers;
        target.permittedSubclasses = source.permittedSubclasses;
        target.recordComponents = source.recordComponents;
        for (int index = 0; index < target.fields.size(); index++) {
            copyInto(target.fields.get(index), source.fields.get(index));
        }
        for (int index = 0; index < target.methods.size(); index++) {
            copyInto(target.methods.get(index), source.methods.get(index));
        }
    }

    private static void copyInto(FieldNode target, FieldNode source) {
        target.access = source.access;
        target.name = source.name;
        target.desc = source.desc;
        target.signature = source.signature;
        target.value = source.value;
        target.visibleAnnotations = source.visibleAnnotations;
        target.invisibleAnnotations = source.invisibleAnnotations;
        target.visibleTypeAnnotations = source.visibleTypeAnnotations;
        target.invisibleTypeAnnotations = source.invisibleTypeAnnotations;
        target.attrs = source.attrs;
    }

    private static void copyInto(MethodNode target, MethodNode source) {
        target.access = source.access;
        target.name = source.name;
        target.desc = source.desc;
        target.signature = source.signature;
        target.exceptions = source.exceptions;
        target.parameters = source.parameters;
        target.visibleAnnotations = source.visibleAnnotations;
        target.invisibleAnnotations = source.invisibleAnnotations;
        target.visibleTypeAnnotations = source.visibleTypeAnnotations;
        target.invisibleTypeAnnotations = source.invisibleTypeAnnotations;
        target.attrs = source.attrs;
        target.annotationDefault = source.annotationDefault;
        target.visibleAnnotableParameterCount = source.visibleAnnotableParameterCount;
        target.visibleParameterAnnotations = source.visibleParameterAnnotations;
        target.invisibleAnnotableParameterCount = source.invisibleAnnotableParameterCount;
        target.invisibleParameterAnnotations = source.invisibleParameterAnnotations;
        target.instructions = source.instructions;
        target.tryCatchBlocks = source.tryCatchBlocks;
        target.maxStack = source.maxStack;
        target.maxLocals = source.maxLocals;
        target.localVariables = source.localVariables;
        target.visibleLocalVariableAnnotations = source.visibleLocalVariableAnnotations;
        target.invisibleLocalVariableAnnotations = source.invisibleLocalVariableAnnotations;
    }

    private static String find(Map<String, String> parents, String name) {
        String root = name;
        while (!parents.get(root).equals(root)) {
            root = parents.get(root);
        }
        parents.put(name, root);
        return root;
    }

    private static void union(Map<String, String> parents, String left, String right) {
        String leftRoot = find(parents, left);
        String rightRoot = find(parents, right);
        if (!leftRoot.equals(rightRoot)) {
            parents.put(rightRoot, leftRoot);
        }
    }

    private abstract static class CountingRemapper extends Remapper {
        private int hits;

        protected String hit(String newName) {
            hits++;
            return newName;
        }
    }
}
