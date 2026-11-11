package io.github.fereshtehdev.springinspector.document;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign;
import io.github.fereshtehdev.springinspector.document.design.SystemDesignScanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small shop application in memory: a web API, JPA, security, Kafka, a scheduled job and an AI assistant. */
public final class SampleProject {

    private final Map<String, String> java = new LinkedHashMap<>();
    private final Map<String, String> settings = new LinkedHashMap<>();

    private SampleProject() {}

    public static SampleProject empty() {
        return new SampleProject();
    }

    public static SampleProject shop() {
        return new SampleProject()
                .java("com/acme/shop/ShopApplication.java", """
                        package com.acme.shop;

                        import org.springframework.boot.autoconfigure.SpringBootApplication;

                        @SpringBootApplication
                        public class ShopApplication {}
                        """)
                .java("com/acme/shop/order/OrderController.java", """
                        package com.acme.shop.order;

                        import org.springframework.http.ResponseEntity;
                        import org.springframework.security.access.prepost.PreAuthorize;
                        import org.springframework.web.bind.annotation.*;

                        @RestController
                        @RequestMapping("/api/orders")
                        public class OrderController {

                            private final OrderService orders;

                            public OrderController(OrderService orders) {
                                this.orders = orders;
                            }

                            @GetMapping("/{id}")
                            public ResponseEntity<OrderView> one(@PathVariable Long id) {
                                return null;
                            }

                            @PostMapping
                            public OrderView create(@RequestBody NewOrder order) {
                                return null;
                            }

                            @DeleteMapping("/{id}")
                            @PreAuthorize("hasRole('ADMIN')")
                            public void delete(@PathVariable Long id) {}

                            @RequestMapping(path = "/search", method = {RequestMethod.GET, RequestMethod.POST})
                            public void search() {}
                        }
                        """)
                .java("com/acme/shop/order/OrderView.java", """
                        package com.acme.shop.order;

                        public record OrderView(Long id) {}
                        """)
                .java("com/acme/shop/order/OrderService.java", """
                        package com.acme.shop.order;

                        import com.acme.shop.payment.PaymentClient;
                        import org.springframework.kafka.core.KafkaTemplate;
                        import org.springframework.stereotype.Service;

                        @Service
                        public class OrderService {

                            private final OrderRepository repository;
                            private final PaymentClient payments;
                            private final KafkaTemplate<String, String> kafka;
                            private OrderView last;

                            public OrderService(
                                    OrderRepository repository, PaymentClient payments, KafkaTemplate<String, String> kafka) {
                                this.repository = repository;
                                this.payments = payments;
                                this.kafka = kafka;
                            }
                        }
                        """)
                .java("com/acme/shop/order/OrderRepository.java", """
                        package com.acme.shop.order;

                        import org.springframework.data.jpa.repository.JpaRepository;

                        public interface OrderRepository extends JpaRepository<Order, Long> {}
                        """)
                .java("com/acme/shop/order/Order.java", """
                        package com.acme.shop.order;

                        import jakarta.persistence.*;
                        import java.util.List;

                        @Entity
                        @Table(name = "orders")
                        public class Order {

                            static final int LIMIT = 10;

                            @Id
                            private Long id;

                            @OneToMany(mappedBy = "order")
                            private List<OrderLine> lines;

                            private String status;
                        }
                        """)
                .java("com/acme/shop/order/OrderLine.java", """
                        package com.acme.shop.order;

                        import jakarta.persistence.*;

                        @Entity
                        public class OrderLine {

                            @Id
                            private Long id;

                            @ManyToOne
                            private Order order;
                        }
                        """)
                .java("com/acme/shop/payment/PaymentClient.java", """
                        package com.acme.shop.payment;

                        import org.springframework.stereotype.Component;
                        import org.springframework.web.client.RestClient;

                        @Component
                        public class PaymentClient {

                            private final RestClient client =
                                    RestClient.builder().baseUrl("https://payments.example.com").build();
                        }
                        """)
                .java("com/acme/shop/payment/RatesClient.java", """
                        package com.acme.shop.payment;

                        import org.springframework.web.service.annotation.GetExchange;
                        import org.springframework.web.service.annotation.HttpExchange;

                        @HttpExchange(url = "https://rates.example.com")
                        public interface RatesClient {

                            @GetExchange("/latest")
                            String latest();
                        }
                        """)
                .java("com/acme/shop/order/OrderEvents.java", """
                        package com.acme.shop.order;

                        import org.springframework.context.event.EventListener;
                        import org.springframework.kafka.annotation.KafkaListener;
                        import org.springframework.scheduling.annotation.Scheduled;
                        import org.springframework.stereotype.Component;

                        @Component
                        public class OrderEvents {

                            private final OrderService orders;

                            public OrderEvents(OrderService orders) {
                                this.orders = orders;
                            }

                            @KafkaListener(topics = {"payments.settled", "payments.failed"})
                            public void onPayment(String orderId) {}

                            @EventListener
                            public void onShipped(OrderShipped event) {}

                            @Scheduled(cron = "0 0 3 * * *")
                            public void expireOldOrders() {}

                            @Scheduled(fixedRate = 5000)
                            public void poll() {}
                        }
                        """)
                .java("com/acme/shop/config/SecurityConfig.java", """
                        package com.acme.shop.config;

                        import org.springframework.context.annotation.Bean;
                        import org.springframework.context.annotation.Configuration;
                        import org.springframework.http.HttpMethod;
                        import org.springframework.security.config.Customizer;
                        import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
                        import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                        import org.springframework.security.config.http.SessionCreationPolicy;
                        import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                        import org.springframework.security.crypto.password.PasswordEncoder;
                        import org.springframework.security.web.SecurityFilterChain;

                        @Configuration
                        @EnableMethodSecurity
                        public class SecurityConfig {

                            @Bean
                            SecurityFilterChain api(HttpSecurity http) throws Exception {
                                return http.csrf(csrf -> csrf.disable())
                                        .formLogin(form -> form.disable())
                                        .sessionManagement(
                                                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                        .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/orders/**")
                                                .hasAnyRole("USER", "ADMIN")
                                                .anyRequest()
                                                .authenticated())
                                        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                                        .build();
                            }

                            @Bean
                            PasswordEncoder passwordEncoder() {
                                return new BCryptPasswordEncoder();
                            }
                        }
                        """)
                .java("com/acme/shop/assistant/AssistantController.java", """
                        package com.acme.shop.assistant;

                        import org.springframework.ai.chat.client.ChatClient;
                        import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
                        import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        public class AssistantController {

                            private final ChatClient chat;
                            private final OrderTools tools;
                            private final PgVectorStore documents;

                            public AssistantController(ChatClient.Builder builder, OrderTools tools, PgVectorStore documents) {
                                this.chat = builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
                                this.tools = tools;
                                this.documents = documents;
                            }

                            @PostMapping("/api/assistant/ask")
                            public String ask(@RequestBody String question) {
                                return "";
                            }
                        }
                        """)
                .java("com/acme/shop/assistant/OrderTools.java", """
                        package com.acme.shop.assistant;

                        import com.acme.shop.order.OrderService;
                        import org.springframework.ai.tool.annotation.Tool;
                        import org.springframework.stereotype.Component;

                        @Component
                        public class OrderTools {

                            private final OrderService orders;

                            public OrderTools(OrderService orders) {
                                this.orders = orders;
                            }

                            @Tool(name = "cancel_order", description = "Cancels the order with this id")
                            public void cancel(Long orderId) {}

                            @Tool
                            public String orderStatus(Long orderId) {
                                return "";
                            }
                        }
                        """)
                .settings("src/main/resources/application.yml", """
                        spring:
                          application:
                            name: shop-service
                          datasource:
                            url: jdbc:postgresql://db.internal:5432/shop?user=shop&password=hunter2
                            username: shop
                            password: hunter2
                          ai:
                            openai:
                              api-key: sk-test-1234567890abcdef
                              chat:
                                options:
                                  model: gpt-5
                            vectorstore:
                              pgvector:
                                dimensions: 1536
                        server:
                          port: 8080
                        """)
                .settings("src/main/resources/application-prod.properties", """
                        spring.data.redis.host=cache.internal
                        spring.ai.ollama.embedding.options.model=${EMBEDDING_MODEL:nomic-embed-text}
                        """);
    }

    public SampleProject java(String pathBelowSources, String content) {
        java.put("src/main/java/" + pathBelowSources, content);
        return this;
    }

    public SampleProject settings(String path, String content) {
        settings.put(path, content);
        return this;
    }

    public Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<>(java);
        files.putAll(settings);
        return files;
    }

    public ImportGraph graph() {
        List<JavaUnit> units = new ArrayList<>();
        java.forEach((path, content) -> units.add(JavaUnit.parse(Path.of(path), content)));
        return new ImportGraph(units);
    }

    public SystemDesign design() {
        return SystemDesignScanner.scan(graph(), sources(java), sources(settings));
    }

    private static List<SourceFile> sources(Map<String, String> files) {
        List<SourceFile> sources = new ArrayList<>();
        files.forEach((path, content) -> sources.add(new SourceFile(Path.of(path), content)));
        return sources;
    }
}
