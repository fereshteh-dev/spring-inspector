package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Integration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** What a type uses to reach other systems: HTTP clients, message producers, mail, object and data stores. */
final class Integrations {

    private static final String HTTP = "HTTP";
    private static final Map<String, String> CLIENTS = clients();

    private Integrations() {}

    /** @param referenced simple names of the types the declaration mentions */
    static List<Integration> of(TypeDeclaration<?> type, Set<String> referenced) {
        String name = type.getNameAsString();
        Optional<Integration> declared = declaredClient(type);
        if (declared.isPresent()) {
            return List.of(declared.get());
        }
        Set<String> technologies = new LinkedHashSet<>();
        CLIENTS.forEach((client, technology) -> {
            if (referenced.contains(client)) {
                technologies.add(technology);
            }
        });
        List<Integration> integrations = new ArrayList<>();
        for (String technology : technologies) {
            integrations.add(new Integration(name, technology, technology.startsWith(HTTP) ? baseUrls(type) : ""));
        }
        return integrations;
    }

    private static Optional<Integration> declaredClient(TypeDeclaration<?> type) {
        return Annotations.find(type, "FeignClient")
                .map(feign -> new Integration(
                        type.getNameAsString(),
                        "HTTP (OpenFeign client)",
                        Annotations.value(feign, "url", "name", "value").orElse("")))
                .or(() -> Annotations.find(type, "HttpExchange")
                        .map(exchange -> new Integration(
                                type.getNameAsString(),
                                "HTTP (declarative interface client)",
                                Annotations.value(exchange, "url", "value").orElse(""))));
    }

    private static String baseUrls(TypeDeclaration<?> type) {
        Set<String> urls = new LinkedHashSet<>();
        for (MethodCallExpr call : type.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("baseUrl") && call.getArguments().size() == 1) {
                urls.add(Annotations.text(call.getArgument(0)));
            }
        }
        return String.join(", ", urls);
    }

    private static Map<String, String> clients() {
        Map<String, String> clients = new LinkedHashMap<>();
        clients.put("RestClient", "HTTP (RestClient)");
        clients.put("WebClient", "HTTP (WebClient)");
        clients.put("RestTemplate", "HTTP (RestTemplate)");
        clients.put("HttpClient", "HTTP (JDK HttpClient)");
        clients.put("KafkaTemplate", "Kafka producer");
        clients.put("RabbitTemplate", "RabbitMQ producer");
        clients.put("AmqpTemplate", "RabbitMQ producer");
        clients.put("JmsTemplate", "JMS producer");
        clients.put("JmsClient", "JMS producer");
        clients.put("StreamBridge", "Spring Cloud Stream producer");
        clients.put("ApplicationEventPublisher", "Application events (publisher)");
        clients.put("JavaMailSender", "E-mail");
        clients.put("S3Client", "Amazon S3");
        clients.put("S3Template", "Amazon S3");
        clients.put("RedisTemplate", "Redis");
        clients.put("StringRedisTemplate", "Redis");
        clients.put("MongoTemplate", "MongoDB");
        clients.put("JdbcTemplate", "SQL (JDBC)");
        clients.put("NamedParameterJdbcTemplate", "SQL (JDBC)");
        clients.put("JdbcClient", "SQL (JDBC)");
        clients.put("ElasticsearchOperations", "Elasticsearch");
        clients.put("ElasticsearchClient", "Elasticsearch");
        return clients;
    }
}
