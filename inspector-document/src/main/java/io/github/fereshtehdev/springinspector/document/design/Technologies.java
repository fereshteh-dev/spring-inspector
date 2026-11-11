package io.github.fereshtehdev.springinspector.document.design;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.ComponentCount;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Technology;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** The frameworks and products a project uses, recognised from what its classes import. */
final class Technologies {

    private static final Map<String, String> BY_PACKAGE = byPackage();

    private Technologies() {}

    static List<Technology> of(ImportGraph graph) {
        Map<String, Integer> classes = new LinkedHashMap<>();
        BY_PACKAGE.values().forEach(name -> classes.putIfAbsent(name, 0));
        for (JavaUnit unit : graph.units()) {
            unit.imports().stream()
                    .map(statement -> technology(statement.name()))
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .forEach(name -> classes.merge(name, 1, Integer::sum));
        }
        List<Technology> technologies = new ArrayList<>();
        classes.forEach((name, count) -> {
            if (count > 0) {
                technologies.add(new Technology(name, count));
            }
        });
        return technologies;
    }

    static List<ComponentCount> components(ImportGraph graph) {
        List<ComponentCount> counts = new ArrayList<>();
        count(graph, "Controllers", unit -> hasAny(unit, "RestController", "Controller"), counts);
        count(graph, "Services", unit -> hasAny(unit, "Service"), counts);
        count(
                graph,
                "Repositories",
                unit -> hasAny(unit, "Repository") || unit.typeName().endsWith("Repository"),
                counts);
        count(graph, "Stored types", unit -> hasAny(unit, "Entity", "Document", "Table"), counts);
        count(graph, "Configuration classes", unit -> hasAny(unit, "Configuration", "SpringBootApplication"), counts);
        count(graph, "Other components", unit -> hasAny(unit, "Component"), counts);
        return counts;
    }

    private static void count(ImportGraph graph, String role, Predicate<JavaUnit> matches, List<ComponentCount> to) {
        int classes = (int) graph.units().stream().filter(matches).count();
        if (classes > 0) {
            to.add(new ComponentCount(role, classes));
        }
    }

    private static boolean hasAny(JavaUnit unit, String... stereotypes) {
        return Set.of(stereotypes).stream().anyMatch(unit.stereotypes()::contains);
    }

    /** The most specific entry wins, so {@code org.springframework.ai.openai} is OpenAI and not just Spring AI. */
    private static String technology(String imported) {
        String best = "";
        String name = "";
        for (Map.Entry<String, String> entry : BY_PACKAGE.entrySet()) {
            String prefix = entry.getKey();
            if ((imported.equals(prefix) || imported.startsWith(prefix + ".")) && prefix.length() > best.length()) {
                best = prefix;
                name = entry.getValue();
            }
        }
        return name;
    }

    private static Map<String, String> byPackage() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("org.springframework.web.bind", "Spring MVC (REST controllers)");
        names.put("org.springframework.web.servlet", "Spring MVC (REST controllers)");
        names.put("org.springframework.web.reactive", "Spring WebFlux (reactive)");
        names.put("reactor.core", "Spring WebFlux (reactive)");
        names.put("org.springframework.graphql", "Spring for GraphQL");
        names.put("org.springframework.web.socket", "WebSocket");
        names.put("jakarta.persistence", "JPA (Hibernate)");
        names.put("javax.persistence", "JPA (Hibernate)");
        names.put("org.springframework.data.jpa", "JPA (Hibernate)");
        names.put("org.springframework.data.jdbc", "Spring Data JDBC");
        names.put("org.springframework.jdbc", "JDBC");
        names.put("org.springframework.data.r2dbc", "R2DBC (reactive SQL)");
        names.put("org.springframework.data.mongodb", "MongoDB");
        names.put("org.springframework.data.redis", "Redis");
        names.put("org.springframework.data.elasticsearch", "Elasticsearch");
        names.put("org.springframework.data.cassandra", "Cassandra");
        names.put("org.springframework.data.neo4j", "Neo4j");
        names.put("org.flywaydb", "Flyway database migrations");
        names.put("liquibase", "Liquibase database migrations");
        names.put("org.springframework.security", "Spring Security");
        names.put("org.springframework.security.oauth2", "OAuth2 / OpenID Connect");
        names.put("jakarta.validation", "Bean Validation");
        names.put("org.springframework.kafka", "Apache Kafka");
        names.put("org.apache.kafka", "Apache Kafka");
        names.put("org.springframework.amqp", "RabbitMQ");
        names.put("org.springframework.jms", "JMS");
        names.put("org.springframework.cloud.stream", "Spring Cloud Stream");
        names.put("org.springframework.cloud", "Spring Cloud");
        names.put("org.springframework.cloud.openfeign", "OpenFeign HTTP clients");
        names.put("org.springframework.batch", "Spring Batch");
        names.put("org.springframework.scheduling", "Scheduling and async execution");
        names.put("org.springframework.cache", "Caching");
        names.put("org.springframework.modulith", "Spring Modulith");
        names.put("org.springframework.mail", "E-mail");
        names.put("io.micrometer", "Micrometer metrics and tracing");
        names.put("io.github.resilience4j", "Resilience4j");
        names.put("org.springframework.retry", "Retry");
        names.put("org.springframework.core.retry", "Retry");
        names.put("io.swagger", "OpenAPI documentation");
        names.put("org.mapstruct", "MapStruct");
        names.put("lombok", "Lombok");
        names.put("org.springframework.ai", "Spring AI");
        names.put("org.springframework.ai.openai", "Spring AI: OpenAI");
        names.put("org.springframework.ai.anthropic", "Spring AI: Anthropic");
        names.put("org.springframework.ai.ollama", "Spring AI: Ollama");
        names.put("org.springframework.ai.azure", "Spring AI: Azure OpenAI");
        names.put("org.springframework.ai.bedrock", "Spring AI: Amazon Bedrock");
        names.put("org.springframework.ai.google", "Spring AI: Google Gemini");
        names.put("org.springframework.ai.vertexai", "Spring AI: Google Vertex AI");
        names.put("org.springframework.ai.mistralai", "Spring AI: Mistral AI");
        names.put("org.springframework.ai.vectorstore", "Spring AI: vector store");
        names.put("org.springframework.ai.mcp", "Model Context Protocol (MCP)");
        names.put("io.modelcontextprotocol", "Model Context Protocol (MCP)");
        return names;
    }
}
