package io.github.fereshtehdev.springinspector.document;

import io.github.fereshtehdev.springinspector.document.content.Diagram;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Integration;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Listener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Draws the application among its neighbours: its callers, its data stores, brokers, AI models and other services. */
final class SystemContext {

    private static final String APPLICATION = "application";
    private static final String SPRING_AI = "Spring AI: ";
    private static final Set<String> SQL =
            Set.of("JPA (Hibernate)", "Spring Data JDBC", "JDBC", "R2DBC (reactive SQL)");
    private static final Set<String> STORES = Set.of("MongoDB", "Redis", "Elasticsearch", "Cassandra", "Neo4j");

    private final List<Diagram.Node> nodes = new ArrayList<>();
    private final List<Diagram.Edge> edges = new ArrayList<>();

    private SystemContext() {}

    static Diagram of(ProjectFacts facts) {
        SystemContext context = new SystemContext();
        context.draw(facts);
        return new Diagram(context.nodes, context.edges);
    }

    private void draw(ProjectFacts facts) {
        SystemDesign design = facts.design();
        if (!design.endpoints().isEmpty()) {
            nodes.add(new Diagram.Node(
                    "clients",
                    "Clients",
                    DocumentComposer.plural(design.endpoints().size(), "endpoint", "endpoints")));
            edges.add(new Diagram.Edge("clients", APPLICATION, "requests", false));
        }
        Map<String, String> brokers = brokers(design);
        brokers.forEach((broker, direction) -> {
            if (direction.contains("in")) {
                neighbour(broker, "message broker");
                edges.add(new Diagram.Edge(broker, APPLICATION, "messages", false));
            }
        });
        nodes.add(new Diagram.Node(
                APPLICATION,
                facts.name(),
                facts.versions().springBoot().map(boot -> "Spring Boot " + boot).orElse("application")));
        brokers.forEach((broker, direction) -> {
            if (direction.contains("out")) {
                if (!direction.contains("in")) {
                    neighbour(broker, "message broker");
                }
                edges.add(new Diagram.Edge(APPLICATION, broker, "publishes", false));
            }
        });
        Set<String> technologies = design.technologies().stream()
                .map(SystemDesign.Technology::name)
                .collect(Collectors.toSet());
        Set<String> stores = new TreeSet<>(design.settings().dataStores());
        STORES.stream().filter(technologies::contains).forEach(stores::add);
        if (stores.stream().noneMatch(SystemContext::isSql)
                && technologies.stream().anyMatch(SQL::contains)) {
            stores.add("SQL database");
        }
        stores.forEach(store -> uses(store, "data store", "reads and writes"));
        Set<String> providers = technologies.stream()
                .filter(name -> name.startsWith(SPRING_AI) && !name.endsWith("vector store"))
                .map(name -> name.substring(SPRING_AI.length()))
                .collect(Collectors.toCollection(TreeSet::new));
        if (providers.isEmpty()) {
            design.ai().models().forEach(model -> providers.add(model.substring(0, model.indexOf(' '))));
        }
        if (providers.isEmpty() && !design.ai().components().isEmpty()) {
            providers.add("Model provider");
        }
        providers.forEach(provider -> uses(provider, "AI model", "prompts"));
        if (!design.ai().vectorStores().isEmpty() || technologies.contains(SPRING_AI + "vector store")) {
            uses("Vector store", String.join(", ", design.ai().vectorStores()), "searches");
        }
        List<Integration> http = design.integrations().stream()
                .filter(integration -> integration.technology().startsWith("HTTP"))
                .toList();
        if (!http.isEmpty()) {
            uses("Other services", DocumentComposer.plural(http.size(), "HTTP client", "HTTP clients"), "calls");
        }
        if (design.integrations().stream()
                .anyMatch(integration -> integration.technology().equals("E-mail"))) {
            uses("Mail server", "", "sends mail");
        }
        if (design.integrations().stream()
                .anyMatch(integration -> integration.technology().equals("Amazon S3"))) {
            uses("Amazon S3", "object store", "stores files");
        }
    }

    private void uses(String name, String detail, String label) {
        neighbour(name, detail);
        edges.add(new Diagram.Edge(APPLICATION, name, label, false));
    }

    private void neighbour(String name, String detail) {
        nodes.add(new Diagram.Node(name, name, detail));
    }

    private static boolean isSql(String store) {
        return !STORES.contains(store);
    }

    /** Each broker with the directions messages flow: {@code in}, {@code out} or {@code in out}. */
    private static Map<String, String> brokers(SystemDesign design) {
        Map<String, String> brokers = new LinkedHashMap<>();
        for (Listener listener : design.listeners()) {
            String broker = listener.kind().substring(0, listener.kind().lastIndexOf(' '));
            if (!listener.kind().startsWith("Application")) {
                brokers.merge(broker, "in", (old, added) -> old.contains("in") ? old : "in " + old);
            }
        }
        for (Integration integration : design.integrations()) {
            if (integration.technology().endsWith(" producer")) {
                String broker = integration
                        .technology()
                        .substring(0, integration.technology().lastIndexOf(' '));
                brokers.merge(broker, "out", (old, added) -> old.contains("out") ? old : old + " out");
            }
        }
        return brokers;
    }
}
