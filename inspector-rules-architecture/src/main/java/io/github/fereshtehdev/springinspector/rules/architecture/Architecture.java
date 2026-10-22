package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit.Import;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The project's structure as the rules analyse it: each class with its layer, the project classes it imports, the
 * dependencies between packages, and the cycles among them. Imports of libraries are left out.
 */
final class Architecture {

    private static final Set<String> HEXAGONAL_SEGMENTS =
            Set.of("adapter", "adapters", "infrastructure", "port", "ports", "application");

    private final ImportGraph graph;
    private final Map<String, JavaUnit> types;
    private final Map<JavaUnit, Layer> layers = new HashMap<>();

    Architecture(ImportGraph graph) {
        this.graph = graph;
        this.types = graph.byQualifiedName();
        graph.units().forEach(unit -> layers.put(unit, Layer.of(unit)));
    }

    List<JavaUnit> units() {
        return graph.units();
    }

    Layer layer(JavaUnit unit) {
        return layers.getOrDefault(unit, Layer.OTHER);
    }

    /** A project class that {@code unit} imports, with the import statement. */
    record Use(JavaUnit from, JavaUnit to, Import statement) {}

    /** The project classes a class imports by name; wildcard imports count once per class of the package. */
    List<Use> uses(JavaUnit unit) {
        List<Use> uses = new ArrayList<>();
        for (Import statement : unit.imports()) {
            if (statement.wildcard()) {
                graph.units().stream()
                        .filter(other -> other.packageName().equals(statement.name()))
                        .forEach(other -> uses.add(new Use(unit, other, statement)));
            } else {
                JavaUnit target = types.get(statement.name());
                if (target != null && !target.equals(unit)) {
                    uses.add(new Use(unit, target, statement));
                }
            }
        }
        return uses;
    }

    /**
     * Whether the project is organised as ports and adapters (hexagonal, clean or onion architecture): it has a
     * {@code domain} package next to {@code adapter}, {@code port}, {@code application} or {@code infrastructure}.
     */
    boolean isHexagonal() {
        boolean domain = false;
        boolean outer = false;
        for (JavaUnit unit : graph.units()) {
            for (String segment : Packages.segments(unit.packageName())) {
                domain |= segment.equals("domain");
                outer |= HEXAGONAL_SEGMENTS.contains(segment);
            }
        }
        return domain && outer;
    }

    /**
     * The first import that makes one package depend on another, for every pair of project packages.
     *
     * @return {@code from package → to package → the import}
     */
    Map<String, Map<String, Use>> packageDependencies() {
        Map<String, Map<String, Use>> edges = new LinkedHashMap<>();
        for (JavaUnit unit : graph.units()) {
            for (Use use : uses(unit)) {
                String from = unit.packageName();
                String to = use.to().packageName();
                if (!from.equals(to)) {
                    edges.computeIfAbsent(from, key -> new LinkedHashMap<>()).putIfAbsent(to, use);
                }
            }
        }
        return edges;
    }

    /** Groups of packages that depend on each other in a circle (Tarjan's strongly connected components). */
    List<Set<String>> cycles() {
        Map<String, Map<String, Use>> edges = packageDependencies();
        Tarjan tarjan = new Tarjan(edges);
        edges.keySet().forEach(tarjan::visitIfNew);
        return tarjan.components.stream()
                .filter(component -> component.size() > 1)
                .toList();
    }

    private static final class Tarjan {

        private final Map<String, Map<String, Use>> edges;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<Set<String>> components = new ArrayList<>();

        Tarjan(Map<String, Map<String, Use>> edges) {
            this.edges = edges;
        }

        void visitIfNew(String node) {
            if (!index.containsKey(node)) {
                visit(node);
            }
        }

        private void visit(String node) {
            int nodeIndex = index.size();
            index.put(node, nodeIndex);
            int low = nodeIndex;
            stack.push(node);
            onStack.add(node);
            for (String next : edges.getOrDefault(node, Map.of()).keySet()) {
                if (!index.containsKey(next)) {
                    visit(next);
                    low = Math.min(low, lowLink.getOrDefault(next, low));
                } else if (onStack.contains(next)) {
                    low = Math.min(low, index.getOrDefault(next, low));
                }
            }
            lowLink.put(node, low);
            if (low == nodeIndex) {
                Set<String> component = new TreeSet<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(component);
            }
        }
    }
}
