package org.de.rikr.flow;

import org.de.rikr.rename.ClassHierarchy;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which methods a method calls, within one JAR. A virtual or interface call can run any override in the JAR, so
 * each override below the receiver's type is a possible target as well.
 */
public final class CallGraph {
    private final ClassHierarchy hierarchy;
    private final Map<MethodRef, List<Call>> cache = new LinkedHashMap<>();

    /**
     * @param inJar false for a method outside the JAR, such as one of the JDK
     */
    public record Call(MethodRef target, CallKind kind, boolean inJar) {
    }

    public enum CallKind {
        STATIC,
        /** A constructor, a private method or a {@code super} call. */
        SPECIAL,
        VIRTUAL,
        INTERFACE,
        /** An override that a virtual or interface call may dispatch to. */
        OVERRIDE,
        /** The body of a lambda or method reference created here. */
        LAMBDA
    }

    public CallGraph(List<ClassNode> classes) {
        this.hierarchy = new ClassHierarchy(classes);
    }

    public Optional<MethodNode> method(MethodRef ref) {
        ClassNode owner = hierarchy.get(ref.owner());
        return owner == null ? Optional.empty()
            : Optional.ofNullable(ClassHierarchy.findMethod(owner, ref.name(), ref.descriptor()));
    }

    public Optional<ClassNode> owner(MethodRef ref) {
        return Optional.ofNullable(hierarchy.get(ref.owner()));
    }

    /**
     * The calls the method makes, each target once, in the order they first appear.
     */
    public List<Call> callees(MethodRef caller) {
        return cache.computeIfAbsent(caller, this::findCallees);
    }

    /**
     * The methods one call instruction may run: the declaration it resolves to, and for a virtual or interface
     * call every override below the receiver's type.
     */
    public List<Call> targets(ControlFlowGraph.CallSite site) {
        Map<String, Call> calls = new LinkedHashMap<>();
        addCall(calls, site.opcode(), site.method().owner(), site.method().name(), site.method().descriptor());
        return List.copyOf(calls.values());
    }

    private List<Call> findCallees(MethodRef caller) {
        Optional<MethodNode> method = method(caller);
        if (method.isEmpty()) {
            return List.of();
        }
        Map<String, Call> calls = new LinkedHashMap<>();
        for (AbstractInsnNode instruction : method.get().instructions) {
            if (instruction instanceof MethodInsnNode call) {
                addCall(calls, call.getOpcode(), call.owner, call.name, call.desc);
            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                addLambda(calls, dynamic);
            }
        }
        return List.copyOf(calls.values());
    }

    private void addCall(Map<String, Call> calls, int opcode, String owner, String name, String descriptor) {
        CallKind kind = switch (opcode) {
            case Opcodes.INVOKESTATIC -> CallKind.STATIC;
            case Opcodes.INVOKESPECIAL -> CallKind.SPECIAL;
            case Opcodes.INVOKEINTERFACE -> CallKind.INTERFACE;
            default -> CallKind.VIRTUAL;
        };
        String resolved = hierarchy.resolveMethod(owner, name, descriptor);
        MethodRef target = new MethodRef(resolved == null ? owner : resolved, name, descriptor);
        calls.putIfAbsent(target.toString(), new Call(target, kind, resolved != null));
        if (kind != CallKind.VIRTUAL && kind != CallKind.INTERFACE) {
            return;
        }
        for (String subtype : hierarchy.selfAndDescendants(owner)) {
            ClassNode classNode = hierarchy.get(subtype);
            if (classNode == null || subtype.equals(target.owner())) {
                continue;
            }
            MethodNode override = ClassHierarchy.findMethod(classNode, name, descriptor);
            if (override != null && ClassHierarchy.isOverridable(override)
                && (override.access & Opcodes.ACC_ABSTRACT) == 0) {
                MethodRef ref = new MethodRef(subtype, name, descriptor);
                calls.putIfAbsent(ref.toString(), new Call(ref, CallKind.OVERRIDE, true));
            }
        }
    }

    private void addLambda(Map<String, Call> calls, InvokeDynamicInsnNode dynamic) {
        if (!"java/lang/invoke/LambdaMetafactory".equals(dynamic.bsm.getOwner())) {
            return;
        }
        for (Object argument : dynamic.bsmArgs) {
            if (argument instanceof Handle handle && hierarchy.contains(handle.getOwner())) {
                MethodRef ref = new MethodRef(handle.getOwner(), handle.getName(), handle.getDesc());
                calls.putIfAbsent(ref.toString(), new Call(ref, CallKind.LAMBDA, true));
            }
        }
    }

    /**
     * The methods within {@code depth} calls of {@code root}, breadth first, each with the calls it makes; at most
     * {@code limit} methods are expanded. Methods at the edge appear only as call targets.
     */
    public Map<MethodRef, List<Call>> reachable(MethodRef root, int depth, int limit, boolean includeLibrary) {
        Map<MethodRef, List<Call>> result = new LinkedHashMap<>();
        List<MethodRef> level = List.of(root);
        for (int step = 0; step < depth && !level.isEmpty(); step++) {
            List<MethodRef> next = new ArrayList<>();
            for (MethodRef method : level) {
                if (result.containsKey(method) || result.size() >= limit) {
                    continue;
                }
                List<Call> calls = callees(method).stream()
                    .filter(call -> includeLibrary || call.inJar())
                    .toList();
                result.put(method, calls);
                calls.stream().filter(Call::inJar).map(Call::target).forEach(next::add);
            }
            level = next;
        }
        return result;
    }
}
