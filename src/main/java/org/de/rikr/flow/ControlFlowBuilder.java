package org.de.rikr.flow;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Splits a method's instructions into basic blocks and finds the edges between them: fall-through, jumps,
 * branches, switch cases and exception handlers.
 */
public final class ControlFlowBuilder {
    private ControlFlowBuilder() {
    }

    public static ControlFlowGraph build(String owner, MethodNode method) {
        InsnList instructions = method.instructions;
        int size = instructions.size();
        if (size == 0) {
            return new ControlFlowGraph(owner, method.name, method.desc, List.of(), List.of());
        }
        Map<LabelNode, Integer> labelIndex = new HashMap<>();
        for (int index = 0; index < size; index++) {
            if (instructions.get(index) instanceof LabelNode label) {
                labelIndex.put(label, index);
            }
        }

        // A block starts at the entry, at every jump, switch or handler target, and after every instruction that
        // ends a block. A handler's covered range also starts and ends blocks, so each block has one set of handlers.
        Set<Integer> leaders = new TreeSet<>();
        leaders.add(0);
        for (int index = 0; index < size; index++) {
            AbstractInsnNode instruction = instructions.get(index);
            List<LabelNode> targets = targets(instruction);
            targets.forEach(target -> leaders.add(labelIndex.get(target)));
            if (!targets.isEmpty() || endsFlow(instruction)) {
                leaders.add(index + 1);
            }
        }
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            leaders.add(labelIndex.get(tryCatch.start));
            leaders.add(labelIndex.get(tryCatch.end));
            leaders.add(labelIndex.get(tryCatch.handler));
        }
        leaders.removeIf(index -> index >= size);

        List<Integer> starts = new ArrayList<>(leaders);
        int[] blockOf = new int[size];
        for (int block = 0; block < starts.size(); block++) {
            int end = block + 1 < starts.size() ? starts.get(block + 1) : size;
            for (int index = starts.get(block); index < end; index++) {
                blockOf[index] = block;
            }
        }

        Printer printer = new Printer(method);
        List<ControlFlowGraph.Edge> edges = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<List<String>> bodies = new ArrayList<>();
        List<List<Integer>> blockLines = new ArrayList<>();
        List<List<ControlFlowGraph.CallSite>> blockCalls = new ArrayList<>();
        for (int block = 0; block < starts.size(); block++) {
            int start = starts.get(block);
            int end = block + 1 < starts.size() ? starts.get(block + 1) : size;
            List<String> lines = new ArrayList<>();
            Set<Integer> lineNumbers = new LinkedHashSet<>();
            List<ControlFlowGraph.CallSite> calls = new ArrayList<>();
            AbstractInsnNode last = null;
            for (int index = start; index < end; index++) {
                AbstractInsnNode instruction = instructions.get(index);
                if (instruction instanceof LineNumberNode lineNumber) {
                    lineNumbers.add(lineNumber.line);
                } else if (!(instruction instanceof LabelNode) && !(instruction instanceof FrameNode)) {
                    lines.add(printer.print(instruction));
                    last = instruction;
                    if (instruction instanceof MethodInsnNode call) {
                        calls.add(new ControlFlowGraph.CallSite(call.getOpcode(),
                            new MethodRef(call.owner, call.name, call.desc)));
                    }
                }
            }
            labels.add(instructions.get(start) instanceof LabelNode label ? printer.labelName(label) : null);
            bodies.add(lines);
            blockLines.add(new ArrayList<>(lineNumbers));
            blockCalls.add(calls);
            addEdges(edges, block, last, end < size ? blockOf[end] : -1, labelIndex, blockOf);
        }
        addHandlerEdges(edges, method, labelIndex, blockOf, starts, size);

        BitSet reachable = reachable(starts.size(), edges);
        // A label after the last return, kept only for debug information, makes an empty block nothing reaches.
        int[] newId = new int[starts.size()];
        List<ControlFlowGraph.Block> blocks = new ArrayList<>();
        for (int block = 0; block < starts.size(); block++) {
            if (bodies.get(block).isEmpty() && !reachable.get(block)) {
                newId[block] = -1;
                continue;
            }
            newId[block] = blocks.size();
            // A switch prints over several lines; give each its own line in the block.
            List<String> lines = bodies.get(block).isEmpty()
                ? List.of("(no instructions)")
                : bodies.get(block).stream().flatMap(String::lines).toList();
            blocks.add(new ControlFlowGraph.Block(blocks.size(), labels.get(block), lines, blockLines.get(block),
                reachable.get(block), blockCalls.get(block)));
        }
        List<ControlFlowGraph.Edge> kept = edges.stream()
            .filter(edge -> newId[edge.from()] >= 0 && newId[edge.to()] >= 0)
            .map(edge -> new ControlFlowGraph.Edge(newId[edge.from()], newId[edge.to()], edge.kind(), edge.label()))
            .toList();
        return new ControlFlowGraph(owner, method.name, method.desc, blocks, kept);
    }

    private static void addEdges(
        List<ControlFlowGraph.Edge> edges,
        int block,
        AbstractInsnNode last,
        int next,
        Map<LabelNode, Integer> labelIndex,
        int[] blockOf
    ) {
        if (last instanceof JumpInsnNode jump) {
            int target = blockOf[labelIndex.get(jump.label)];
            if (jump.getOpcode() == Opcodes.GOTO) {
                edges.add(new ControlFlowGraph.Edge(block, target, ControlFlowGraph.EdgeKind.JUMP, null));
                return;
            }
            if (jump.getOpcode() == Opcodes.JSR) {
                // An old-style subroutine call: it jumps, and RET returns to the next instruction.
                edges.add(new ControlFlowGraph.Edge(block, target, ControlFlowGraph.EdgeKind.JUMP, "jsr"));
            } else {
                edges.add(new ControlFlowGraph.Edge(block, target, ControlFlowGraph.EdgeKind.TRUE, null));
            }
            if (next >= 0) {
                edges.add(new ControlFlowGraph.Edge(block, next, jump.getOpcode() == Opcodes.JSR
                    ? ControlFlowGraph.EdgeKind.FALLTHROUGH : ControlFlowGraph.EdgeKind.FALSE, null));
            }
            return;
        }
        if (last instanceof TableSwitchInsnNode table) {
            for (int index = 0; index < table.labels.size(); index++) {
                edges.add(new ControlFlowGraph.Edge(block, blockOf[labelIndex.get(table.labels.get(index))],
                    ControlFlowGraph.EdgeKind.SWITCH, String.valueOf(table.min + index)));
            }
            edges.add(new ControlFlowGraph.Edge(block, blockOf[labelIndex.get(table.dflt)],
                ControlFlowGraph.EdgeKind.SWITCH, "default"));
            return;
        }
        if (last instanceof LookupSwitchInsnNode lookup) {
            for (int index = 0; index < lookup.labels.size(); index++) {
                edges.add(new ControlFlowGraph.Edge(block, blockOf[labelIndex.get(lookup.labels.get(index))],
                    ControlFlowGraph.EdgeKind.SWITCH, String.valueOf(lookup.keys.get(index))));
            }
            edges.add(new ControlFlowGraph.Edge(block, blockOf[labelIndex.get(lookup.dflt)],
                ControlFlowGraph.EdgeKind.SWITCH, "default"));
            return;
        }
        if (last != null && endsFlow(last)) {
            return;
        }
        if (next >= 0) {
            edges.add(new ControlFlowGraph.Edge(block, next, ControlFlowGraph.EdgeKind.FALLTHROUGH, null));
        }
    }

    private static void addHandlerEdges(
        List<ControlFlowGraph.Edge> edges,
        MethodNode method,
        Map<LabelNode, Integer> labelIndex,
        int[] blockOf,
        List<Integer> starts,
        int size
    ) {
        Set<String> added = new LinkedHashSet<>();
        for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
            int from = labelIndex.get(tryCatch.start);
            int to = labelIndex.get(tryCatch.end);
            int handler = blockOf[labelIndex.get(tryCatch.handler)];
            String type = tryCatch.type == null ? "any" : tryCatch.type;
            for (int block = 0; block < starts.size(); block++) {
                int start = starts.get(block);
                if (start >= from && start < to && start < size && added.add(block + ">" + handler + ":" + type)) {
                    edges.add(new ControlFlowGraph.Edge(block, handler, ControlFlowGraph.EdgeKind.EXCEPTION, type));
                }
            }
        }
    }

    private static BitSet reachable(int blocks, List<ControlFlowGraph.Edge> edges) {
        Map<Integer, List<Integer>> successors = new HashMap<>();
        for (ControlFlowGraph.Edge edge : edges) {
            successors.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
        }
        BitSet seen = new BitSet(blocks);
        Deque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        while (!pending.isEmpty()) {
            int block = pending.removeFirst();
            if (seen.get(block)) {
                continue;
            }
            seen.set(block);
            pending.addAll(successors.getOrDefault(block, List.of()));
        }
        return seen;
    }

    private static List<LabelNode> targets(AbstractInsnNode instruction) {
        if (instruction instanceof JumpInsnNode jump) {
            return List.of(jump.label);
        }
        if (instruction instanceof TableSwitchInsnNode table) {
            List<LabelNode> targets = new ArrayList<>(table.labels);
            targets.add(table.dflt);
            return targets;
        }
        if (instruction instanceof LookupSwitchInsnNode lookup) {
            List<LabelNode> targets = new ArrayList<>(lookup.labels);
            targets.add(lookup.dflt);
            return targets;
        }
        return List.of();
    }

    private static boolean endsFlow(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        return (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW
            || opcode == Opcodes.RET || opcode == Opcodes.GOTO;
    }

    /**
     * Prints single instructions as the bytecode view does, with the same label names: the whole method is printed
     * once to name its labels in the order the bytecode view meets them, and those names are reused.
     */
    private static final class Printer {
        private final NamingTextifier textifier = new NamingTextifier();
        private final TraceMethodVisitor visitor = new TraceMethodVisitor(textifier);
        private final Map<Label, String> names;

        Printer(MethodNode method) {
            NamingTextifier whole = new NamingTextifier();
            method.accept(new TraceMethodVisitor(whole));
            names = whole.names();
            textifier.useNames(names);
        }

        String labelName(LabelNode label) {
            return names.getOrDefault(label.getLabel(), "L?");
        }

        String print(AbstractInsnNode instruction) {
            textifier.getText().clear();
            instruction.accept(visitor);
            StringBuilder text = new StringBuilder();
            for (Object part : textifier.getText()) {
                text.append(part);
            }
            return text.toString().strip();
        }
    }

    private static final class NamingTextifier extends Textifier {
        NamingTextifier() {
            super(Opcodes.ASM9);
        }

        Map<Label, String> names() {
            return labelNames == null ? Map.of() : Map.copyOf(labelNames);
        }

        void useNames(Map<Label, String> names) {
            labelNames = new HashMap<>(names);
        }
    }
}
