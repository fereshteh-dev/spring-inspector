package io.github.fereshtehdev.springinspector.intellij.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command line's {@code architecture --format json} report: the project's styles, parts, cycles, overloaded
 * classes and suggestions, plus the same report as Markdown with a Mermaid diagram.
 */
public record ArchitectureView(
        String rootPackage,
        int classes,
        List<Style> styles,
        List<Part> parts,
        List<List<String>> cycles,
        List<Hotspot> hotspots,
        List<Suggestion> suggestions,
        String markdown) {

    public ArchitectureView {
        styles = List.copyOf(styles);
        parts = List.copyOf(parts);
        cycles = cycles.stream().map(List::copyOf).toList();
        hotspots = List.copyOf(hotspots);
        suggestions = List.copyOf(suggestions);
    }

    public record Style(String title, String description, String confidence, List<String> evidence) {

        public Style {
            evidence = List.copyOf(evidence);
        }
    }

    public record Part(String name, int classes, Map<String, Integer> dependsOn) {

        public Part {
            // In the report's order, which Map.copyOf would shuffle.
            dependsOn = Collections.unmodifiableMap(new LinkedHashMap<>(dependsOn));
        }
    }

    public record Hotspot(String type, int usedBy, int uses) {}

    public record Suggestion(String title, String why, List<String> steps) {

        public Suggestion {
            steps = List.copyOf(steps);
        }
    }

    /** Reads the report; throws {@link IllegalArgumentException} when the text is not one. */
    public static ArchitectureView read(String json) {
        JsonTree root = JsonTree.parse(json);
        if (root.get("styles").items().isEmpty() && root.get("rootPackage").optionalText().isEmpty()) {
            throw new IllegalArgumentException("The output is not an architecture report");
        }
        return new ArchitectureView(
                root.get("rootPackage").text(),
                root.get("classes").number(),
                root.get("styles").items().stream()
                        .map(style -> new Style(
                                style.get("title").text(),
                                style.get("description").text(),
                                style.get("confidence").text(),
                                texts(style.get("evidence"))))
                        .toList(),
                root.get("parts").items().stream()
                        .map(part -> new Part(part.get("name").text(), part.get("classes").number(), counts(part)))
                        .toList(),
                root.get("cycles").items().stream().map(ArchitectureView::texts).toList(),
                root.get("hotspots").items().stream()
                        .map(hotspot -> new Hotspot(
                                hotspot.get("type").text(),
                                hotspot.get("usedBy").number(),
                                hotspot.get("uses").number()))
                        .toList(),
                root.get("suggestions").items().stream()
                        .map(suggestion -> new Suggestion(
                                suggestion.get("title").text(),
                                suggestion.get("why").text(),
                                texts(suggestion.get("steps"))))
                        .toList(),
                root.get("markdown").text());
    }

    private static List<String> texts(JsonTree array) {
        return array.items().stream().map(JsonTree::text).toList();
    }

    private static Map<String, Integer> counts(JsonTree part) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        part.get("dependsOn").names().forEach(name -> counts.put(name, part.get("dependsOn").get(name).number()));
        return counts;
    }
}
