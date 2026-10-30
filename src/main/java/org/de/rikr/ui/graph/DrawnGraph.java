package org.de.rikr.ui.graph;

import java.awt.Color;
import java.util.List;

/**
 * What a {@link GraphView} draws: boxes of text joined by arrows.
 */
public record DrawnGraph(List<Node> nodes, List<Edge> edges) {
    public DrawnGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /**
     * @param lines   the first line is the title, drawn in {@code titleColor}
     * @param dim     drawn faded with a dashed outline, for unreachable code or methods outside the JAR
     * @param marked  drawn with a strong outline, for the method or block the graph starts from
     * @param badge   a short label in the top right corner that can be clicked, or null
     * @param tooltip shown when hovering, as plain text, or null
     */
    public record Node(
        List<String> lines,
        Color titleColor,
        boolean dim,
        boolean marked,
        String badge,
        String tooltip
    ) {
        public Node {
            lines = List.copyOf(lines);
        }
    }

    public record Edge(int from, int to, Color color, boolean dashed, String label) {
    }
}
