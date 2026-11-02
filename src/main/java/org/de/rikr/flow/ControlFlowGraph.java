package org.de.rikr.flow;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The basic blocks of one method and the ways control passes between them. Block 0 is the entry.
 */
public record ControlFlowGraph(String owner, String method, String descriptor, List<Block> blocks, List<Edge> edges) {
    public ControlFlowGraph {
        blocks = List.copyOf(blocks);
        edges = List.copyOf(edges);
    }

    /**
     * The graph without exception handler edges, and without the blocks only a handler leads to, so the normal paths
     * stand out. Dead code stays. Blocks are renumbered; the entry stays block 0.
     */
    public ControlFlowGraph withoutExceptionEdges() {
        boolean[] keep = new boolean[blocks.size()];
        Deque<Integer> pending = new ArrayDeque<>(List.of(0));
        while (!pending.isEmpty()) {
            int block = pending.removeFirst();
            if (keep[block]) {
                continue;
            }
            keep[block] = true;
            for (Edge edge : edges) {
                if (edge.from() == block && edge.kind() != EdgeKind.EXCEPTION) {
                    pending.add(edge.to());
                }
            }
        }
        for (Block block : blocks) {
            keep[block.id()] |= !block.reachable();
        }
        int[] newId = new int[blocks.size()];
        List<Block> kept = new ArrayList<>();
        for (Block block : blocks) {
            newId[block.id()] = keep[block.id()] ? kept.size() : -1;
            if (keep[block.id()]) {
                kept.add(new Block(kept.size(), block.label(), block.lines(), block.lineNumbers(), block.reachable(),
                    block.calls()));
            }
        }
        List<Edge> keptEdges = edges.stream()
            .filter(edge -> edge.kind() != EdgeKind.EXCEPTION && newId[edge.from()] >= 0 && newId[edge.to()] >= 0)
            .map(edge -> new Edge(newId[edge.from()], newId[edge.to()], edge.kind(), edge.label()))
            .toList();
        return new ControlFlowGraph(owner, method, descriptor, kept, keptEdges);
    }

    /**
     * Instructions that run in sequence: control only enters at the first and only leaves after the last.
     *
     * @param label     the label that starts the block, such as {@code L3}, or null for the entry
     * @param lines     the block's instructions as ASM prints them, without line numbers and frames
     * @param lineNumbers the source lines the block's instructions come from, when the class has them
     * @param reachable false when no path from the entry leads here, which obfuscators often leave behind
     * @param calls     the method calls the block makes, in order
     */
    public record Block(
        int id,
        String label,
        List<String> lines,
        List<Integer> lineNumbers,
        boolean reachable,
        List<CallSite> calls
    ) {
        public Block {
            lines = List.copyOf(lines);
            lineNumbers = List.copyOf(lineNumbers);
            calls = List.copyOf(calls);
        }

        /**
         * Whether control leaves the method from this block: it returns or throws, with no successor.
         */
        public boolean exits(List<Edge> edges) {
            return edges.stream().noneMatch(edge -> edge.from() == id && edge.kind() != EdgeKind.EXCEPTION);
        }
    }

    /**
     * A method call instruction, with the method as the instruction names it, before resolution.
     */
    public record CallSite(int opcode, MethodRef method) {
    }

    /**
     * @param label what selects this edge: a switch key, or the exception type a handler catches
     */
    public record Edge(int from, int to, EdgeKind kind, String label) {
    }

    public enum EdgeKind {
        /** The next block in the code, reached by falling through. */
        FALLTHROUGH,
        /** An unconditional jump. */
        JUMP,
        /** A conditional jump that is taken. */
        TRUE,
        /** A conditional jump that is not taken. */
        FALSE,
        /** A switch case or its default. */
        SWITCH,
        /** An exception handler that covers the block. */
        EXCEPTION
    }
}
