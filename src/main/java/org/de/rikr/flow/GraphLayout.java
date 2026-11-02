package org.de.rikr.flow;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Places a graph top to bottom in layers, the usual way flow and call graphs are drawn:
 * <ol>
 *   <li>Edges that jump back to a block already on the path from the entry (loops) are set aside, so the rest
 *   is acyclic.</li>
 *   <li>Each block goes one layer below its lowest predecessor, so forward edges always point down.</li>
 *   <li>Blocks in a layer are reordered towards the average position of their neighbours, which removes most
 *   crossings.</li>
 *   <li>Blocks are placed below the middle of their predecessors where there is room.</li>
 * </ol>
 * Forward edges are drawn down between layers; loop edges go up a lane to the right of everything.
 */
public final class GraphLayout {
    static final int LAYER_GAP = 56;
    static final int BLOCK_GAP = 36;
    static final int MARGIN = 24;
    private static final int LANE_GAP = 10;
    private static final int ORDERING_SWEEPS = 8;
    private static final int PLACEMENT_ROUNDS = 4;

    private GraphLayout() {
    }

    /**
     * @param boxes  where each block goes
     * @param routes the points each edge passes through, from its source to its target
     * @param loops  which edges go back up to an earlier block
     */
    public record Result(List<Rectangle> boxes, List<List<Point>> routes, List<Boolean> loops, int width, int height) {
    }

    /**
     * An edge to lay out, from one node to another by index.
     */
    public record Link(int from, int to) {
    }

    public static Result layout(ControlFlowGraph graph, List<Dimension> sizes) {
        return layout(graph.blocks().size(),
            graph.edges().stream().map(edge -> new Link(edge.from(), edge.to())).toList(), sizes);
    }

    public static Result layout(int count, List<Link> edges, List<Dimension> sizes) {
        return layout(count, edges, sizes, Integer.MAX_VALUE);
    }

    /**
     * @param maxPerRow the most nodes side by side; a wider layer wraps onto further rows, so a method that calls
     *                  many others does not make the graph too wide to read
     */
    public static Result layout(int count, List<Link> edges, List<Dimension> sizes, int maxPerRow) {
        if (count == 0) {
            return new Result(List.of(), List.of(), List.of(), 2 * MARGIN, 2 * MARGIN);
        }
        boolean[] loop = findLoopEdges(count, edges);
        int[] layer = assignLayers(count, edges, loop);
        List<List<Integer>> layers = wrap(orderLayers(count, edges, loop, layer), maxPerRow);

        Rectangle[] boxes = new Rectangle[count];
        int y = MARGIN;
        for (List<Integer> blocks : layers) {
            int height = 0;
            for (int block : blocks) {
                Dimension size = sizes.get(block);
                boxes[block] = new Rectangle(0, y, size.width, size.height);
                height = Math.max(height, size.height);
            }
            y += height + LAYER_GAP;
        }
        placeHorizontally(layers, edges, loop, boxes);

        int right = Arrays.stream(boxes).mapToInt(box -> box.x + box.width).max().orElse(0);
        List<List<Point>> routes = new ArrayList<>();
        List<Boolean> loops = new ArrayList<>();
        int[] outgoing = new int[count];
        int[] outgoingSeen = new int[count];
        for (Link edge : edges) {
            outgoing[edge.from()]++;
        }
        int lanes = 0;
        for (int index = 0; index < edges.size(); index++) {
            Link edge = edges.get(index);
            Rectangle from = boxes[edge.from()];
            Rectangle to = boxes[edge.to()];
            loops.add(loop[index]);
            if (loop[index]) {
                // Up a lane of its own to the right of the graph, into the target's right side.
                int laneX = right + MARGIN + LANE_GAP * lanes++;
                int startY = from.y + from.height / 2;
                int endY = to.y + Math.min(14, to.height / 2);
                routes.add(List.of(new Point(from.x + from.width, startY), new Point(laneX, startY),
                    new Point(laneX, endY), new Point(to.x + to.width, endY)));
                continue;
            }
            // Spread a block's outgoing edges along its bottom, then drop to just above the target.
            int slot = ++outgoingSeen[edge.from()];
            int startX = from.x + from.width * slot / (outgoing[edge.from()] + 1);
            int startY = from.y + from.height;
            int endX = to.x + to.width / 2;
            int endY = to.y;
            int bendY = startY + LAYER_GAP / 2;
            int approachY = endY - LAYER_GAP / 2;
            if (approachY <= bendY) {
                routes.add(List.of(new Point(startX, startY), new Point(startX, bendY), new Point(endX, bendY),
                    new Point(endX, endY)));
            } else {
                routes.add(List.of(new Point(startX, startY), new Point(startX, bendY), new Point(endX, approachY),
                    new Point(endX, endY)));
            }
        }
        int width = right + MARGIN + LANE_GAP * lanes + MARGIN;
        int height = Arrays.stream(boxes).mapToInt(box -> box.y + box.height).max().orElse(0) + MARGIN;
        return new Result(List.of(boxes), routes, loops, width, height);
    }

    /**
     * Depth-first from the entry, then from any block not yet visited: an edge to a block still on the search path
     * closes a loop.
     */
    private static boolean[] findLoopEdges(int count, List<Link> edges) {
        List<List<Integer>> outgoing = outgoing(count, edges);
        boolean[] loop = new boolean[edges.size()];
        int[] state = new int[count];
        for (int root = 0; root < count; root++) {
            if (state[root] != 0) {
                continue;
            }
            Deque<int[]> stack = new ArrayDeque<>();
            stack.push(new int[]{root, 0});
            state[root] = 1;
            while (!stack.isEmpty()) {
                int[] frame = stack.peek();
                List<Integer> next = outgoing.get(frame[0]);
                if (frame[1] == next.size()) {
                    state[frame[0]] = 2;
                    stack.pop();
                    continue;
                }
                int edgeIndex = next.get(frame[1]++);
                int target = edges.get(edgeIndex).to();
                if (state[target] == 1) {
                    loop[edgeIndex] = true;
                } else if (state[target] == 0) {
                    state[target] = 1;
                    stack.push(new int[]{target, 0});
                }
            }
        }
        return loop;
    }

    /**
     * The longest path from a source over the edges that are not loops, so every forward edge points down.
     */
    private static int[] assignLayers(int count, List<Link> edges, boolean[] loop) {
        int[] incoming = new int[count];
        for (int index = 0; index < edges.size(); index++) {
            if (!loop[index]) {
                incoming[edges.get(index).to()]++;
            }
        }
        List<List<Integer>> outgoing = outgoing(count, edges);
        int[] layer = new int[count];
        Deque<Integer> ready = new ArrayDeque<>();
        for (int block = 0; block < count; block++) {
            if (incoming[block] == 0) {
                ready.add(block);
            }
        }
        while (!ready.isEmpty()) {
            int block = ready.removeFirst();
            for (int edgeIndex : outgoing.get(block)) {
                if (loop[edgeIndex]) {
                    continue;
                }
                int target = edges.get(edgeIndex).to();
                layer[target] = Math.max(layer[target], layer[block] + 1);
                if (--incoming[target] == 0) {
                    ready.add(target);
                }
            }
        }
        return layer;
    }

    private static List<List<Integer>> orderLayers(
        int count,
        List<Link> edges,
        boolean[] loop,
        int[] layer
    ) {
        int depth = Arrays.stream(layer).max().orElse(0) + 1;
        List<List<Integer>> layers = new ArrayList<>();
        for (int index = 0; index < depth; index++) {
            layers.add(new ArrayList<>());
        }
        for (int block = 0; block < count; block++) {
            layers.get(layer[block]).add(block);
        }
        double[] position = new double[count];
        for (List<Integer> blocks : layers) {
            for (int index = 0; index < blocks.size(); index++) {
                position[blocks.get(index)] = index;
            }
        }
        for (int sweep = 0; sweep < ORDERING_SWEEPS; sweep++) {
            boolean down = sweep % 2 == 0;
            for (int step = 1; step < depth; step++) {
                List<Integer> blocks = layers.get(down ? step : depth - 1 - step);
                double[] barycenter = new double[count];
                for (int block : blocks) {
                    double sum = 0;
                    int neighbours = 0;
                    for (int index = 0; index < edges.size(); index++) {
                        Link edge = edges.get(index);
                        if (loop[index]) {
                            continue;
                        }
                        if (down && edge.to() == block) {
                            sum += position[edge.from()];
                            neighbours++;
                        } else if (!down && edge.from() == block) {
                            sum += position[edge.to()];
                            neighbours++;
                        }
                    }
                    barycenter[block] = neighbours == 0 ? position[block] : sum / neighbours;
                }
                blocks.sort(Comparator.comparingDouble((Integer block) -> barycenter[block])
                    .thenComparingDouble(block -> position[block]));
                for (int index = 0; index < blocks.size(); index++) {
                    position[blocks.get(index)] = index;
                }
            }
        }
        return layers;
    }

    /**
     * Put each block under the middle of its predecessors, then over the middle of its successors, a few times
     * over. Each layer keeps its order and the gap between blocks; after packing, a layer moves back by its average
     * overshoot, so wide layers do not push the rest of the graph ever further right.
     */
    private static void placeHorizontally(
        List<List<Integer>> layers,
        List<Link> edges,
        boolean[] loop,
        Rectangle[] boxes
    ) {
        for (List<Integer> blocks : layers) {
            pack(blocks, boxes, new double[boxes.length], false);
        }
        for (int round = 0; round < PLACEMENT_ROUNDS; round++) {
            boolean down = round % 2 == 0;
            for (int step = 0; step < layers.size(); step++) {
                List<Integer> blocks = layers.get(down ? step : layers.size() - 1 - step);
                double[] wanted = new double[boxes.length];
                for (int block : blocks) {
                    double sum = 0;
                    int neighbours = 0;
                    for (int index = 0; index < edges.size(); index++) {
                        Link edge = edges.get(index);
                        if (loop[index]) {
                            continue;
                        }
                        if (down && edge.to() == block && edge.from() != block) {
                            sum += boxes[edge.from()].getCenterX();
                            neighbours++;
                        } else if (!down && edge.from() == block && edge.to() != block) {
                            sum += boxes[edge.to()].getCenterX();
                            neighbours++;
                        }
                    }
                    // A block with no neighbours on this side stays where it is.
                    wanted[block] = (neighbours == 0 ? boxes[block].getCenterX() : sum / neighbours)
                        - boxes[block].width / 2.0;
                }
                pack(blocks, boxes, wanted, true);
            }
        }
        int left = Arrays.stream(boxes).mapToInt(box -> box.x).min().orElse(MARGIN);
        for (Rectangle box : boxes) {
            box.x += MARGIN - left;
        }
    }

    /**
     * Place a layer's blocks left to right at their wanted positions where the gap allows, then move the layer back
     * by how far packing pushed it right on average.
     */
    private static void pack(List<Integer> blocks, Rectangle[] boxes, double[] wanted, boolean useWanted) {
        double right = Double.NEGATIVE_INFINITY;
        double overshoot = 0;
        for (int block : blocks) {
            double x = useWanted ? Math.max(wanted[block], right) : Math.max(MARGIN, right);
            if (useWanted) {
                overshoot += x - wanted[block];
            }
            boxes[block].x = (int) Math.round(x);
            right = x + boxes[block].width + BLOCK_GAP;
        }
        if (useWanted && !blocks.isEmpty()) {
            int shift = (int) Math.round(overshoot / blocks.size());
            for (int block : blocks) {
                boxes[block].x -= shift;
            }
        }
    }

    private static List<List<Integer>> wrap(List<List<Integer>> layers, int maxPerRow) {
        List<List<Integer>> rows = new ArrayList<>();
        for (List<Integer> layer : layers) {
            for (int start = 0; start < layer.size(); start += maxPerRow) {
                rows.add(new ArrayList<>(layer.subList(start, Math.min(layer.size(), start + maxPerRow))));
            }
        }
        return rows;
    }

    private static List<List<Integer>> outgoing(int count, List<Link> edges) {
        List<List<Integer>> outgoing = new ArrayList<>();
        for (int block = 0; block < count; block++) {
            outgoing.add(new ArrayList<>());
        }
        for (int index = 0; index < edges.size(); index++) {
            outgoing.get(edges.get(index).from()).add(index);
        }
        return outgoing;
    }
}
