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
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The project's structure as the rules analyse it: each class with its layer, the project classes it imports, the
 * dependencies between packages, and the cycles among them. Imports of libraries are left out.
 */
final class Architecture {

    private static final Set<String> HEXAGONAL_SEGMENTS =
            Set.of("adapter", "adapters", "infrastructure", "port", "ports", "application");

    /** Package names below the root that hold technical code rather than a business feature. */
    static final Set<String> TECHNICAL_SEGMENTS = Set.of(
            "config",
            "configuration",
            "common",
            "shared",
            "util",
            "utils",
            "support",
            "core",
            "infrastructure",
            "adapter",
            "adapters",
            "port",
            "ports",
            "application",
            "domain",
            "web",
            "controller",
            "controllers",
            "rest",
            "api",
            "service",
            "services",
            "repository",
            "repositories",
            "repo",
            "dao",
            "persistence",
            "model",
            "entity",
            "entities",
            "dto",
            "dtos",
            "exception",
            "exceptions",
            "security",
            "internal",
            "usecase",
            "usecases",
            "presentation",
            "gateway",
            "gateways",
            "event",
            "events",
            "command",
            "commands",
            "query",
            "queries",
            "mapper",
            "mappers");

    static final String PACKAGE_INFO = "package-info";

    private final ImportGraph graph;
    private final List<JavaUnit> packageInfos;
    private final Map<String, JavaUnit> types;
    private final Map<String, List<JavaUnit>> byPackage = new HashMap<>();
    private final Map<JavaUnit, Layer> layers = new HashMap<>();
    private final Map<JavaUnit, List<Use>> uses = new HashMap<>();
    private final String rootPackage;

    Architecture(ImportGraph graph) {
        this.graph = new ImportGraph(graph.units().stream()
                .filter(unit -> !unit.typeName().equals(PACKAGE_INFO))
                .toList());
        this.packageInfos = graph.units().stream()
                .filter(unit -> unit.typeName().equals(PACKAGE_INFO))
                .toList();
        this.types = this.graph.byQualifiedName();
        this.graph.units().forEach(unit -> {
            layers.put(unit, Layer.of(unit));
            byPackage
                    .computeIfAbsent(unit.packageName(), key -> new ArrayList<>())
                    .add(unit);
        });
        this.rootPackage = findRootPackage(this.graph.units());
    }

    List<JavaUnit> units() {
        return graph.units();
    }

    /** The {@code package-info.java} files, where package annotations such as Spring Modulith's live. */
    List<JavaUnit> packageInfos() {
        return packageInfos;
    }

    Layer layer(JavaUnit unit) {
        return layers.getOrDefault(unit, Layer.OTHER);
    }

    /**
     * The application's root package: where its {@code @SpringBootApplication} lives when there is one, else the
     * longest package all classes share. Empty when classes share nothing.
     */
    String rootPackage() {
        return rootPackage;
    }

    /** The first package level below the root, such as {@code orders} in {@code com.acme.orders.web}. */
    Optional<String> topSegment(JavaUnit unit) {
        String name = unit.packageName();
        if (rootPackage.isEmpty()) {
            List<String> segments = Packages.segments(name);
            return segments.isEmpty() ? Optional.empty() : Optional.of(segments.getFirst());
        }
        if (!name.startsWith(rootPackage + ".")) {
            return Optional.empty();
        }
        List<String> below = Packages.segments(name.substring(rootPackage.length() + 1));
        return below.isEmpty() ? Optional.empty() : Optional.of(below.getFirst());
    }

    /** The business feature a class belongs to: its top package below the root, unless that is technical. */
    Optional<String> featureOf(JavaUnit unit) {
        return topSegment(unit).filter(segment -> !TECHNICAL_SEGMENTS.contains(segment));
    }

    /** The package a feature's public API lives in: the feature's own package, directly below the root. */
    String featurePackage(String feature) {
        return rootPackage.isEmpty() ? feature : rootPackage + "." + feature;
    }

    /** A project class that {@code unit} imports, with the import statement. */
    record Use(JavaUnit from, JavaUnit to, Import statement) {}

    /** The project classes a class imports by name; wildcard imports count once per class of the package. */
    List<Use> uses(JavaUnit unit) {
        return uses.computeIfAbsent(unit, this::resolve);
    }

    private List<Use> resolve(JavaUnit unit) {
        List<Use> resolved = new ArrayList<>();
        for (Import statement : unit.imports()) {
            if (statement.wildcard()) {
                byPackage.getOrDefault(statement.name(), List.of()).stream()
                        .filter(other -> !other.equals(unit))
                        .forEach(other -> resolved.add(new Use(unit, other, statement)));
            } else {
                JavaUnit target = types.get(statement.name());
                if (target != null && !target.equals(unit)) {
                    resolved.add(new Use(unit, target, statement));
                }
            }
        }
        return resolved;
    }

    private static String findRootPackage(List<JavaUnit> units) {
        List<String> applications = units.stream()
                .filter(unit -> unit.stereotypes().contains("SpringBootApplication"))
                .map(JavaUnit::packageName)
                .distinct()
                .toList();
        if (applications.size() == 1) {
            return applications.getFirst();
        }
        List<String> packages = (applications.isEmpty()
                        ? units.stream().map(JavaUnit::packageName)
                        : applications.stream())
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
        if (packages.isEmpty()) {
            return "";
        }
        List<String> common = Packages.segments(packages.getFirst());
        for (String name : packages) {
            List<String> segments = Packages.segments(name);
            int shared = 0;
            while (shared < common.size()
                    && shared < segments.size()
                    && common.get(shared).equals(segments.get(shared))) {
                shared++;
            }
            common = common.subList(0, shared);
        }
        return String.join(".", common);
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
