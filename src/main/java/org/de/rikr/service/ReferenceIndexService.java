package org.de.rikr.service;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ReferenceIndexService {
    private final Map<String, ReferenceIndex> indexes = new HashMap<>();

    public void indexJar(String jarName, List<ClassNode> classes) {
        Objects.requireNonNull(jarName, "jarName");
        Objects.requireNonNull(classes, "classes");
        ReferenceIndex index = buildIndex(classes);
        synchronized (indexes) {
            indexes.put(jarName, index);
        }
    }

    public void removeJar(String jarName) {
        synchronized (indexes) {
            indexes.remove(jarName);
        }
    }

    public List<ReferenceResult> findFieldReads(
        String jarName,
        ClassNode owner,
        FieldNode field
    ) {
        return getIndex(jarName).fieldReads().getOrDefault(memberKey(owner, field.name, field.desc), List.of());
    }

    public List<ReferenceResult> findFieldWrites(
        String jarName,
        ClassNode owner,
        FieldNode field
    ) {
        return getIndex(jarName).fieldWrites().getOrDefault(memberKey(owner, field.name, field.desc), List.of());
    }

    public List<ReferenceResult> findMethodCallers(
        String jarName,
        ClassNode owner,
        MethodNode method
    ) {
        return getIndex(jarName).methodCallers().getOrDefault(memberKey(owner, method.name, method.desc), List.of());
    }

    public List<ReferenceResult> findMethodCallees(
        String jarName,
        ClassNode owner,
        MethodNode method
    ) {
        return getIndex(jarName).methodCallees().getOrDefault(memberKey(owner, method.name, method.desc), List.of());
    }

    public List<ReferenceResult> findTypeReferences(String jarName, ClassNode targetClass) {
        Objects.requireNonNull(targetClass, "targetClass");
        return getIndex(jarName).typeReferences().getOrDefault(targetClass.name, List.of());
    }

    private ReferenceIndex buildIndex(List<ClassNode> classes) {
        Map<String, ClassNode> classesByName = new HashMap<>();
        for (ClassNode classNode : classes) {
            if (classNode != null && classNode.name != null) {
                classesByName.put(classNode.name, classNode);
            }
        }

        MutableIndex index = new MutableIndex();
        for (ClassNode classNode : classes) {
            if (classNode != null) {
                indexClass(classNode, classesByName, index);
            }
        }
        return index.freeze();
    }

    private void indexClass(ClassNode classNode, Map<String, ClassNode> classesByName, MutableIndex index) {
        if (classNode.superName != null) {
            addTypeReference(
                index,
                classNode.superName,
                ReferenceKind.EXTENDS,
                classNode.name + " extends " + classNode.superName,
                classNode,
                ClassNodeUtil.getPattern(classNode, null, null),
                classNode.name
            );
        }
        for (String interfaceName : classNode.interfaces) {
            addTypeReference(
                index,
                interfaceName,
                ReferenceKind.IMPLEMENTS,
                classNode.name + " implements " + interfaceName,
                classNode,
                ClassNodeUtil.getPattern(classNode, null, null),
                classNode.name
            );
        }

        for (FieldNode field : classNode.fields) {
            addDescriptorTypes(field.desc, typeName -> addTypeReference(
                index,
                typeName,
                ReferenceKind.FIELD_TYPE,
                classNode.name + "." + field.name + " " + field.desc,
                classNode,
                ClassNodeUtil.getFieldNodePattern(field),
                field.name
            ));
        }

        for (MethodNode method : classNode.methods) {
            indexMethod(classNode, method, classesByName, index);
        }
    }

    private void indexMethod(
        ClassNode sourceClass,
        MethodNode sourceMethod,
        Map<String, ClassNode> classesByName,
        MutableIndex index
    ) {
        addMethodDescriptorTypes(sourceClass, sourceMethod, index);
        for (TryCatchBlockNode tryCatchBlock : sourceMethod.tryCatchBlocks) {
            if (tryCatchBlock.type != null) {
                addMethodTypeReference(index, tryCatchBlock.type, sourceClass, sourceMethod);
            }
        }

        for (AbstractInsnNode instruction : sourceMethod.instructions) {
            if (instruction instanceof FieldInsnNode fieldInstruction) {
                indexFieldInstruction(sourceClass, sourceMethod, fieldInstruction, index);
            } else if (instruction instanceof MethodInsnNode methodInstruction) {
                indexMethodInstruction(sourceClass, sourceMethod, methodInstruction, classesByName, index);
            } else if (instruction instanceof TypeInsnNode typeInstruction) {
                addInstructionTypeReference(index, typeInstruction.desc, sourceClass, sourceMethod);
            } else if (instruction instanceof MultiANewArrayInsnNode arrayInstruction) {
                addDescriptorTypes(arrayInstruction.desc, typeName ->
                    addInstructionTypeReference(index, typeName, sourceClass, sourceMethod)
                );
            } else if (instruction instanceof LdcInsnNode ldcInstruction && ldcInstruction.cst instanceof Type type) {
                addType(type, typeName -> addInstructionTypeReference(index, typeName, sourceClass, sourceMethod));
            } else if (instruction instanceof InvokeDynamicInsnNode dynamicInstruction) {
                addDescriptorTypes(dynamicInstruction.desc, typeName ->
                    addInstructionTypeReference(index, typeName, sourceClass, sourceMethod)
                );
            }
        }
    }

    private void addMethodDescriptorTypes(ClassNode sourceClass, MethodNode sourceMethod, MutableIndex index) {
        addDescriptorTypes(sourceMethod.desc, typeName -> addTypeReference(
            index,
            typeName,
            ReferenceKind.METHOD_TYPE,
            sourceClass.name + "." + sourceMethod.name + sourceMethod.desc,
            sourceClass,
            ClassNodeUtil.getMethodNodePattern(sourceMethod),
            sourceMethod.name
        ));
    }

    private void indexFieldInstruction(
        ClassNode sourceClass,
        MethodNode sourceMethod,
        FieldInsnNode instruction,
        MutableIndex index
    ) {
        ReferenceKind kind = instruction.getOpcode() == Opcodes.GETFIELD
            || instruction.getOpcode() == Opcodes.GETSTATIC
            ? ReferenceKind.FIELD_READ
            : ReferenceKind.FIELD_WRITE;
        ReferenceResult result = sourceMemberResult(
            kind,
            sourceClass,
            sourceMethod,
            (kind == ReferenceKind.FIELD_READ ? "reads " : "writes ")
                + instruction.owner + "." + instruction.name + " " + instruction.desc
        );
        Map<MemberKey, Set<ReferenceResult>> target = kind == ReferenceKind.FIELD_READ
            ? index.fieldReads
            : index.fieldWrites;
        add(target, new MemberKey(instruction.owner, instruction.name, instruction.desc), result);
        addInstructionTypeReference(index, instruction.owner, sourceClass, sourceMethod);
        addDescriptorTypes(instruction.desc, typeName ->
            addInstructionTypeReference(index, typeName, sourceClass, sourceMethod)
        );
    }

    private void indexMethodInstruction(
        ClassNode sourceClass,
        MethodNode sourceMethod,
        MethodInsnNode instruction,
        Map<String, ClassNode> classesByName,
        MutableIndex index
    ) {
        MemberKey targetKey = new MemberKey(instruction.owner, instruction.name, instruction.desc);
        ReferenceResult caller = sourceMemberResult(
            ReferenceKind.CALLER,
            sourceClass,
            sourceMethod,
            "calls " + instruction.owner + "." + instruction.name + instruction.desc
        );
        add(index.methodCallers, targetKey, caller);

        ClassNode targetClass = classesByName.get(instruction.owner);
        ReferenceResult callee = new ReferenceResult(
            ReferenceKind.CALLEE,
            instruction.owner + "." + instruction.name + instruction.desc,
            targetClass,
            targetClass == null ? null : instruction.name + instruction.desc,
            targetClass == null ? null : instruction.name
        );
        add(index.methodCallees, memberKey(sourceClass, sourceMethod.name, sourceMethod.desc), callee);

        addInstructionTypeReference(index, instruction.owner, sourceClass, sourceMethod);
        addDescriptorTypes(instruction.desc, typeName ->
            addInstructionTypeReference(index, typeName, sourceClass, sourceMethod)
        );
    }

    private ReferenceResult sourceMemberResult(
        ReferenceKind kind,
        ClassNode sourceClass,
        MethodNode sourceMethod,
        String relationship
    ) {
        return new ReferenceResult(
            kind,
            sourceClass.name + "." + sourceMethod.name + sourceMethod.desc + " -> " + relationship,
            sourceClass,
            ClassNodeUtil.getMethodNodePattern(sourceMethod),
            sourceMethod.name
        );
    }

    private void addMethodTypeReference(
        MutableIndex index,
        String typeName,
        ClassNode sourceClass,
        MethodNode sourceMethod
    ) {
        addTypeReference(
            index,
            typeName,
            ReferenceKind.METHOD_TYPE,
            sourceClass.name + "." + sourceMethod.name + sourceMethod.desc,
            sourceClass,
            ClassNodeUtil.getMethodNodePattern(sourceMethod),
            sourceMethod.name
        );
    }

    private void addInstructionTypeReference(
        MutableIndex index,
        String typeName,
        ClassNode sourceClass,
        MethodNode sourceMethod
    ) {
        addTypeReference(
            index,
            normalizeTypeName(typeName),
            ReferenceKind.INSTRUCTION_TYPE,
            sourceClass.name + "." + sourceMethod.name + sourceMethod.desc + " -> uses " + typeName,
            sourceClass,
            ClassNodeUtil.getMethodNodePattern(sourceMethod),
            sourceMethod.name
        );
    }

    private void addTypeReference(
        MutableIndex index,
        String typeName,
        ReferenceKind kind,
        String text,
        ClassNode navigationClass,
        String navigationPattern,
        String highlightText
    ) {
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        add(index.typeReferences, typeName, new ReferenceResult(
            kind,
            text,
            navigationClass,
            navigationPattern,
            highlightText
        ));
    }

    private void addDescriptorTypes(String descriptor, Consumer<String> consumer) {
        Type type = Type.getType(descriptor);
        if (type.getSort() == Type.METHOD) {
            for (Type argumentType : type.getArgumentTypes()) {
                addType(argumentType, consumer);
            }
            addType(type.getReturnType(), consumer);
        } else {
            addType(type, consumer);
        }
    }

    private void addType(Type type, Consumer<String> consumer) {
        Type current = type;
        while (current.getSort() == Type.ARRAY) {
            current = current.getElementType();
        }
        if (current.getSort() == Type.OBJECT) {
            consumer.accept(current.getInternalName());
        } else if (current.getSort() == Type.METHOD) {
            for (Type argumentType : current.getArgumentTypes()) {
                addType(argumentType, consumer);
            }
            addType(current.getReturnType(), consumer);
        }
    }

    private String normalizeTypeName(String typeName) {
        if (typeName != null && typeName.startsWith("[")) {
            Type type = Type.getType(typeName);
            while (type.getSort() == Type.ARRAY) {
                type = type.getElementType();
            }
            return type.getSort() == Type.OBJECT ? type.getInternalName() : null;
        }
        return typeName;
    }

    private MemberKey memberKey(ClassNode owner, String name, String descriptor) {
        Objects.requireNonNull(owner, "owner");
        return new MemberKey(owner.name, name, descriptor);
    }

    private ReferenceIndex getIndex(String jarName) {
        synchronized (indexes) {
            return indexes.getOrDefault(jarName, ReferenceIndex.empty());
        }
    }

    private <K> void add(Map<K, Set<ReferenceResult>> map, K key, ReferenceResult result) {
        map.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(result);
    }

    private record MemberKey(String owner, String name, String descriptor) {
    }

    private record ReferenceIndex(
        Map<MemberKey, List<ReferenceResult>> fieldReads,
        Map<MemberKey, List<ReferenceResult>> fieldWrites,
        Map<MemberKey, List<ReferenceResult>> methodCallers,
        Map<MemberKey, List<ReferenceResult>> methodCallees,
        Map<String, List<ReferenceResult>> typeReferences
    ) {
        private static ReferenceIndex empty() {
            return new ReferenceIndex(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private static class MutableIndex {
        private final Map<MemberKey, Set<ReferenceResult>> fieldReads = new HashMap<>();
        private final Map<MemberKey, Set<ReferenceResult>> fieldWrites = new HashMap<>();
        private final Map<MemberKey, Set<ReferenceResult>> methodCallers = new HashMap<>();
        private final Map<MemberKey, Set<ReferenceResult>> methodCallees = new HashMap<>();
        private final Map<String, Set<ReferenceResult>> typeReferences = new HashMap<>();

        private ReferenceIndex freeze() {
            return new ReferenceIndex(
                freezeMap(fieldReads),
                freezeMap(fieldWrites),
                freezeMap(methodCallers),
                freezeMap(methodCallees),
                freezeMap(typeReferences)
            );
        }

        private static <K> Map<K, List<ReferenceResult>> freezeMap(Map<K, Set<ReferenceResult>> source) {
            Map<K, List<ReferenceResult>> frozen = new HashMap<>();
            for (Map.Entry<K, Set<ReferenceResult>> entry : source.entrySet()) {
                List<ReferenceResult> results = new ArrayList<>(entry.getValue());
                results.sort(Comparator.comparing(ReferenceResult::text));
                frozen.put(entry.getKey(), List.copyOf(results));
            }
            return Map.copyOf(frozen);
        }
    }
}
