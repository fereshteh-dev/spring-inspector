package io.github.fereshtehdev.springinspector.document.render;

import io.github.fereshtehdev.springinspector.document.content.Diagram;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places a diagram's boxes in columns from left to right, so that arrows mostly point right, and routes the arrows
 * between them. The HTML and PDF writers draw the same placement, so both formats show the same picture.
 */
final class DiagramLayout {

    static final int LABEL_LIMIT = 34;
    private static final double MARGIN = 12;
    private static final double COLUMN_GAP = 96;
    private static final double ROW_GAP = 22;
    private static final double MUTUAL_OFFSET = 7;

    private DiagramLayout() {}

    /** A box with its top-left corner; {@code label} and {@code detail} are shortened to fit. */
    record Box(String label, String detail, double x, double y, double width, double height) {

        double centerX() {
            return x + width / 2;
        }

        double centerY() {
            return y + height / 2;
        }
    }

    /** A curve from the first point to the last, bent by the two control points in between. */
    record Arrow(
            double x1,
            double y1,
            double controlX1,
            double controlY1,
            double controlX2,
            double controlY2,
            double x2,
            double y2,
            String label,
            boolean problem) {

        double labelX() {
            return (x1 + 3 * controlX1 + 3 * controlX2 + x2) / 8;
        }

        double labelY() {
            return (y1 + 3 * controlY1 + 3 * controlY2 + y2) / 8;
        }
    }

    record Placed(List<Box> boxes, List<Arrow> arrows, double width, double height) {}

    static Placed of(Diagram diagram) {
        Map<String, Integer> index = new LinkedHashMap<>();
        diagram.nodes().forEach(node -> index.putIfAbsent(node.id(), index.size()));
        List<Diagram.Edge> edges = diagram.edges().stream()
                .filter(edge -> index.containsKey(edge.from())
                        && index.containsKey(edge.to())
                        && !edge.from().equals(edge.to()))
                .toList();
        Map<String, Integer> column = columns(index.keySet(), edges);
        Map<String, Box> boxes = place(diagram.nodes(), index, column, edges);
        List<Arrow> arrows = new ArrayList<>();
        for (Diagram.Edge edge : edges) {
            boolean mutual = edges.stream()
                    .anyMatch(other ->
                            other.from().equals(edge.to()) && other.to().equals(edge.from()));
            arrows.add(route(edge, required(boxes, edge.from()), required(boxes, edge.to()), mutual));
        }
        double width = boxes.values().stream()
                .mapToDouble(box -> box.x() + box.width())
                .max()
                .orElse(0);
        double height = boxes.values().stream()
                .mapToDouble(box -> box.y() + box.height())
                .max()
                .orElse(0);
        return new Placed(List.copyOf(boxes.values()), arrows, width + MARGIN, height + MARGIN);
    }

    /** Each node's column: one further right than the furthest of the nodes that point at it. */
    private static Map<String, Integer> columns(Set<String> ids, List<Diagram.Edge> edges) {
        List<Diagram.Edge> forward = withoutCycles(ids, edges);
        Map<String, Integer> column = new HashMap<>();
        ids.forEach(id -> column.put(id, 0));
        // Without cycles the longest path has fewer edges than there are nodes, so this settles.
        for (int round = 0; round < ids.size(); round++) {
            boolean moved = false;
            for (Diagram.Edge edge : forward) {
                int needed = column.getOrDefault(edge.from(), 0) + 1;
                if (column.getOrDefault(edge.to(), 0) < needed) {
                    column.put(edge.to(), needed);
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return column;
    }

    /** The edges left after dropping, in drawing order, each one that would close a circle. */
    private static List<Diagram.Edge> withoutCycles(Set<String> ids, List<Diagram.Edge> edges) {
        Map<String, Set<String>> reachable = new HashMap<>();
        ids.forEach(id -> reachable.put(id, new HashSet<>(Set.of(id))));
        List<Diagram.Edge> kept = new ArrayList<>();
        for (Diagram.Edge edge : edges) {
            if (reachable.getOrDefault(edge.to(), Set.of()).contains(edge.from())) {
                continue;
            }
            kept.add(edge);
            Set<String> gained = reachable.getOrDefault(edge.to(), Set.of());
            for (Set<String> from : reachable.values()) {
                if (from.contains(edge.from())) {
                    from.addAll(gained);
                }
            }
        }
        return kept;
    }

    private static Map<String, Box> place(
            List<Diagram.Node> nodes,
            Map<String, Integer> index,
            Map<String, Integer> column,
            List<Diagram.Edge> edges) {
        Map<Integer, List<Diagram.Node>> byColumn = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Diagram.Node node : nodes) {
            if (seen.add(node.id())) {
                byColumn.computeIfAbsent(column.getOrDefault(node.id(), 0), key -> new ArrayList<>())
                        .add(node);
            }
        }
        int columns =
                byColumn.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        Map<String, Double> row = new HashMap<>();
        List<List<Diagram.Node>> ordered = new ArrayList<>();
        for (int at = 0; at < columns; at++) {
            List<Diagram.Node> inColumn = new ArrayList<>(byColumn.getOrDefault(at, List.of()));
            // Keep a node near the nodes that point at it, so arrows cross less.
            inColumn.sort(Comparator.comparingDouble((Diagram.Node node) -> edges.stream()
                            .filter(edge -> edge.to().equals(node.id()) && row.containsKey(edge.from()))
                            .mapToDouble(edge -> row.getOrDefault(edge.from(), 0.0))
                            .average()
                            .orElse(index.getOrDefault(node.id(), 0)))
                    .thenComparingInt(node -> index.getOrDefault(node.id(), 0)));
            for (int position = 0; position < inColumn.size(); position++) {
                row.put(inColumn.get(position).id(), position - (inColumn.size() - 1) / 2.0);
            }
            ordered.add(inColumn);
        }
        double tallest =
                ordered.stream().mapToDouble(DiagramLayout::height).max().orElse(0);
        Map<String, Box> boxes = new LinkedHashMap<>();
        double x = MARGIN;
        for (List<Diagram.Node> inColumn : ordered) {
            double widest =
                    inColumn.stream().mapToDouble(DiagramLayout::width).max().orElse(0);
            double y = MARGIN + (tallest - height(inColumn)) / 2;
            for (Diagram.Node node : inColumn) {
                double height = height(node);
                boxes.put(
                        node.id(),
                        new Box(
                                shorten(node.label()),
                                shorten(node.detail()),
                                x + (widest - width(node)) / 2,
                                y,
                                width(node),
                                height));
                y += height + ROW_GAP;
            }
            x += widest + (inColumn.isEmpty() ? 0 : COLUMN_GAP);
        }
        return boxes;
    }

    private static Arrow route(Diagram.Edge edge, Box from, Box to, boolean mutual) {
        if (Math.abs(from.x() - to.x()) < 1) {
            boolean down = from.y() < to.y();
            double x = from.centerX() + (mutual ? (down ? -MUTUAL_OFFSET : MUTUAL_OFFSET) : 0);
            double y1 = down ? from.y() + from.height() : from.y();
            double y2 = down ? to.y() : to.y() + to.height();
            double third = (y2 - y1) / 3;
            return new Arrow(x, y1, x, y1 + third, x, y2 - third, x, y2, edge.label(), edge.problem());
        }
        boolean right = from.x() < to.x();
        double shift = mutual ? (right ? -MUTUAL_OFFSET : MUTUAL_OFFSET) : 0;
        double x1 = right ? from.x() + from.width() : from.x();
        double x2 = right ? to.x() : to.x() + to.width();
        double y1 = from.centerY() + shift;
        double y2 = to.centerY() + shift;
        double half = (x2 - x1) / 2;
        return new Arrow(x1, y1, x1 + half, y1, x2 - half, y2, x2, y2, edge.label(), edge.problem());
    }

    private static Box required(Map<String, Box> boxes, String id) {
        Box box = boxes.get(id);
        if (box == null) {
            throw new IllegalStateException("No box was placed for " + id);
        }
        return box;
    }

    private static double height(List<Diagram.Node> column) {
        return column.stream().mapToDouble(DiagramLayout::height).sum() + ROW_GAP * Math.max(0, column.size() - 1);
    }

    private static double height(Diagram.Node node) {
        return node.detail().isEmpty() ? 32 : 46;
    }

    private static double width(Diagram.Node node) {
        double text = Math.max(
                shorten(node.label()).length() * 6.4, shorten(node.detail()).length() * 5.2);
        return Math.max(92, text + 24);
    }

    static String shorten(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= LABEL_LIMIT ? oneLine : oneLine.substring(0, LABEL_LIMIT - 3) + "...";
    }
}
