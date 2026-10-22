package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A picture of a project's architecture: the styles it follows, its parts and how they depend on each other, its
 * cycles and overloaded classes, and suggestions for a cleaner structure. Built from the same analysis as the
 * architecture rules, so the report and the findings agree.
 *
 * @param parts the features of the project, or its top-level packages when it has no features
 * @param cycles packages that depend on each other in a circle, relative to the root package
 * @param diagram a Mermaid flowchart of the parts and their dependencies; cycles are drawn in red
 */
public record ArchitectureReport(
        String rootPackage,
        int classes,
        List<StyleMatch> styles,
        List<Part> parts,
        List<List<String>> cycles,
        List<Hotspot> hotspots,
        List<Suggestion> suggestions,
        String diagram) {

    static final int HOTSPOT_FAN_IN = 10;
    static final int HOTSPOT_FAN_OUT = 10;
    private static final int LISTED = 5;
    private static final List<String> ROLE_SUFFIXES = List.of(
            "RestController",
            "Controller",
            "ServiceImpl",
            "Service",
            "Repository",
            "Dao",
            "Entity",
            "Dto",
            "Request",
            "Response",
            "Mapper",
            "Impl");

    public ArchitectureReport {
        styles = List.copyOf(styles);
        parts = List.copyOf(parts);
        cycles = cycles.stream().map(List::copyOf).toList();
        hotspots = List.copyOf(hotspots);
        suggestions = List.copyOf(suggestions);
    }

    /**
     * A recognised style.
     *
     * @param confidence {@code high} or {@code medium}
     * @param evidence what the style was recognised from
     */
    public record StyleMatch(ArchitectureStyle style, String confidence, List<String> evidence) {

        public StyleMatch {
            evidence = List.copyOf(evidence);
        }
    }

    /**
     * A feature or top-level package.
     *
     * @param dependsOn other parts it imports from, with the number of classes that do
     */
    public record Part(String name, int classes, Map<String, Integer> dependsOn) {

        public Part {
            // Sorted, so the report and its diagram come out the same on every run.
            dependsOn = Collections.unmodifiableMap(new TreeMap<>(dependsOn));
        }
    }

    /** A class many others depend on that also depends on many: changes to it are risky and it does too much. */
    public record Hotspot(String type, int usedBy, int uses) {}

    /** A concrete step towards a cleaner structure. */
    public record Suggestion(String title, String why, List<String> steps) {

        public Suggestion {
            steps = List.copyOf(steps);
        }
    }

    public static ArchitectureReport of(ImportGraph graph, int buildModules) {
        Architecture architecture = new Architecture(graph);
        List<StyleMatch> styles = StyleDetector.detect(architecture, buildModules);
        List<Part> parts = parts(architecture, styles);
        List<List<String>> cycles = architecture.cycles().stream()
                .map(cycle ->
                        cycle.stream().map(name -> relative(architecture, name)).toList())
                .toList();
        List<Hotspot> hotspots = hotspots(architecture);
        List<Suggestion> suggestions = Advisor.suggest(architecture, styles, cycles, hotspots);
        return new ArchitectureReport(
                architecture.rootPackage(),
                graph.units().size(),
                styles,
                parts,
                cycles,
                hotspots,
                suggestions,
                diagram(parts, cycles));
    }

    public boolean follows(ArchitectureStyle style) {
        return styles.stream().anyMatch(match -> match.style() == style);
    }

    private static List<Part> parts(Architecture architecture, List<StyleMatch> styles) {
        boolean byFeature = styles.stream().anyMatch(match -> match.style() == ArchitectureStyle.PACKAGE_BY_FEATURE);
        Map<String, Integer> sizes = new TreeMap<>();
        Map<String, Map<String, Set<JavaUnit>>> edges = new TreeMap<>();
        for (JavaUnit unit : architecture.units()) {
            Optional<String> part = byFeature ? architecture.featureOf(unit) : architecture.topSegment(unit);
            if (part.isEmpty()) {
                continue;
            }
            sizes.merge(part.get(), 1, Integer::sum);
            for (Architecture.Use use : architecture.uses(unit)) {
                Optional<String> target =
                        byFeature ? architecture.featureOf(use.to()) : architecture.topSegment(use.to());
                target.filter(name -> !name.equals(part.get()))
                        .ifPresent(name -> edges.computeIfAbsent(part.get(), key -> new TreeMap<>())
                                .computeIfAbsent(name, key -> new HashSet<>())
                                .add(unit));
            }
        }
        List<Part> parts = new ArrayList<>();
        sizes.forEach((name, size) -> {
            Map<String, Integer> dependsOn = new TreeMap<>();
            edges.getOrDefault(name, Map.of()).forEach((target, users) -> dependsOn.put(target, users.size()));
            parts.add(new Part(name, size, dependsOn));
        });
        return parts;
    }

    private static List<Hotspot> hotspots(Architecture architecture) {
        Map<JavaUnit, Set<JavaUnit>> usedBy = new HashMap<>();
        Map<JavaUnit, Set<JavaUnit>> uses = new HashMap<>();
        for (JavaUnit unit : architecture.units()) {
            for (Architecture.Use use : architecture.uses(unit)) {
                uses.computeIfAbsent(unit, key -> new HashSet<>()).add(use.to());
                usedBy.computeIfAbsent(use.to(), key -> new HashSet<>()).add(unit);
            }
        }
        return architecture.units().stream()
                .map(unit -> new Hotspot(
                        unit.qualifiedName(),
                        usedBy.getOrDefault(unit, Set.of()).size(),
                        uses.getOrDefault(unit, Set.of()).size()))
                .filter(hotspot -> hotspot.usedBy() >= HOTSPOT_FAN_IN && hotspot.uses() >= HOTSPOT_FAN_OUT)
                .sorted(Comparator.comparingInt((Hotspot hotspot) -> hotspot.usedBy() + hotspot.uses())
                        .reversed())
                .limit(LISTED)
                .toList();
    }

    /** The business name in a class name: {@code Order} for {@code OrderController} or {@code OrderRepository}. */
    static String baseName(String typeName) {
        for (String suffix : ROLE_SUFFIXES) {
            if (typeName.endsWith(suffix) && typeName.length() > suffix.length()) {
                return typeName.substring(0, typeName.length() - suffix.length());
            }
        }
        return typeName;
    }

    static String relative(Architecture architecture, String packageName) {
        String root = architecture.rootPackage();
        if (root.isEmpty() || packageName.equals(root)) {
            return packageName;
        }
        return packageName.startsWith(root + ".") ? packageName.substring(root.length() + 1) : packageName;
    }

    private static String diagram(List<Part> parts, List<List<String>> cycles) {
        Set<String> inCycle = new TreeSet<>();
        cycles.forEach(cycle -> cycle.forEach(name -> inCycle.add(
                Packages.segments(name).isEmpty()
                        ? name
                        : Packages.segments(name).getFirst())));
        StringBuilder mermaid = new StringBuilder("flowchart LR\n");
        Map<String, String> ids = new LinkedHashMap<>();
        for (Part part : parts) {
            String id = "p" + ids.size();
            ids.put(part.name(), id);
            mermaid.append("    ")
                    .append(id)
                    .append("[\"")
                    .append(part.name())
                    .append("<br/>")
                    .append(part.classes())
                    .append(part.classes() == 1 ? " class" : " classes")
                    .append("\"]\n");
        }
        List<Integer> cycleLinks = new ArrayList<>();
        int link = 0;
        for (Part part : parts) {
            for (Map.Entry<String, Integer> dependency : part.dependsOn().entrySet()) {
                String target = ids.get(dependency.getKey());
                if (target == null) {
                    continue;
                }
                boolean mutual = parts.stream()
                        .anyMatch(other -> other.name().equals(dependency.getKey())
                                && other.dependsOn().containsKey(part.name()));
                mermaid.append("    ")
                        .append(ids.get(part.name()))
                        .append(" -->|")
                        .append(dependency.getValue())
                        .append("| ")
                        .append(target)
                        .append('\n');
                if (mutual || (inCycle.contains(part.name()) && inCycle.contains(dependency.getKey()))) {
                    cycleLinks.add(link);
                }
                link++;
            }
        }
        if (!cycleLinks.isEmpty()) {
            mermaid.append("    linkStyle ")
                    .append(String.join(
                            ",", cycleLinks.stream().map(String::valueOf).toList()))
                    .append(" stroke:#d33,stroke-width:2px\n");
        }
        return mermaid.toString();
    }
}
