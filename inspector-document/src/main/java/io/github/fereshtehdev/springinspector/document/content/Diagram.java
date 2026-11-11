package io.github.fereshtehdev.springinspector.document.content;

import java.util.List;

/** Boxes and arrows, drawn left to right. */
public record Diagram(List<Node> nodes, List<Edge> edges) {

    public Diagram {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /**
     * A box.
     *
     * @param detail a second, smaller line, or empty
     */
    public record Node(String id, String label, String detail) {}

    /**
     * An arrow between two nodes.
     *
     * @param label a short text on the arrow, or empty
     * @param problem drawn in red, such as a dependency that is part of a cycle
     */
    public record Edge(String from, String to, String label, boolean problem) {}
}
