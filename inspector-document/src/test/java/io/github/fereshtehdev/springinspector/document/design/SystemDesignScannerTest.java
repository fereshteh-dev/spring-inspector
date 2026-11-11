package io.github.fereshtehdev.springinspector.document.design;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.document.SampleProject;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AccessRule;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiComponent;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiTool;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.ComponentCount;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Endpoint;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.EntityModel;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Flow;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Integration;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Listener;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Relation;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.ScheduledJob;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Technology;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemDesignScannerTest {

    private final SystemDesign design = SampleProject.shop().design();

    @Test
    void findsTheApplicationClassAndCountsItsBuildingBlocks() {
        assertThat(design.applications()).containsExactly("ShopApplication");
        assertThat(design.components())
                .contains(
                        new ComponentCount("Controllers", 2),
                        new ComponentCount("Services", 1),
                        new ComponentCount("Repositories", 1),
                        new ComponentCount("Stored types", 2));
    }

    @Test
    void recognisesTechnologiesFromImports() {
        assertThat(design.technologies())
                .extracting(Technology::name)
                .contains(
                        "Spring MVC (REST controllers)",
                        "JPA (Hibernate)",
                        "Spring Security",
                        "Apache Kafka",
                        "Spring AI",
                        "Spring AI: vector store");
    }

    @Test
    void listsEndpointsWithPathBodyResponseAndAccessRule() {
        assertThat(design.endpoints())
                .contains(
                        new Endpoint("GET", "/api/orders/{id}", "OrderController.one", "", "OrderView", ""),
                        new Endpoint("POST", "/api/orders", "OrderController.create", "NewOrder", "OrderView", ""),
                        new Endpoint(
                                "DELETE", "/api/orders/{id}", "OrderController.delete", "", "void", "hasRole('ADMIN')"),
                        new Endpoint("GET, POST", "/api/orders/search", "OrderController.search", "", "void", ""),
                        new Endpoint("POST", "/api/assistant/ask", "AssistantController.ask", "String", "String", ""));
    }

    @Test
    void doesNotTakeADeclarativeHttpClientForAnEndpoint() {
        assertThat(design.endpoints()).extracting(Endpoint::handler).noneMatch(handler -> handler.startsWith("Rates"));
        assertThat(design.integrations())
                .contains(new Integration(
                        "RatesClient", "HTTP (declarative interface client)", "https://rates.example.com"));
    }

    @Test
    void describesStoredTypesWithTheirRelations() {
        assertThat(design.entities())
                .containsExactly(
                        new EntityModel(
                                "Order", "orders", "id", 3, List.of(new Relation("OneToMany", "lines", "OrderLine"))),
                        new EntityModel(
                                "OrderLine", "", "id", 2, List.of(new Relation("ManyToOne", "order", "Order"))));
    }

    @Test
    void listsMessageAndEventListenersAndScheduledJobs() {
        assertThat(design.listeners())
                .containsExactly(
                        new Listener("Kafka topic", "payments.settled, payments.failed", "OrderEvents.onPayment"),
                        new Listener("Application event", "OrderShipped", "OrderEvents.onShipped"));
        assertThat(design.jobs())
                .containsExactly(
                        new ScheduledJob("cron 0 0 3 * * *", "OrderEvents.expireOldOrders"),
                        new ScheduledJob("every 5000 ms", "OrderEvents.poll"));
    }

    @Test
    void listsWhatTalksToOtherSystems() {
        assertThat(design.integrations())
                .contains(
                        new Integration("OrderService", "Kafka producer", ""),
                        new Integration("PaymentClient", "HTTP (RestClient)", "https://payments.example.com"));
    }

    @Test
    void readsAccessRulesInTheOrderTheyAreTried() {
        assertThat(design.security().rules())
                .containsExactly(
                        new AccessRule("/actuator/health", "anyone", "SecurityConfig.api"),
                        new AccessRule("GET, /api/orders/**", "role USER or ADMIN", "SecurityConfig.api"),
                        new AccessRule("every other request", "signed-in users", "SecurityConfig.api"));
    }

    @Test
    void describesSecurityFeaturesAndLeavesOutDisabledOnes() {
        assertThat(design.security().features())
                .containsExactlyInAnyOrder(
                        "Method security is enabled (@PreAuthorize and similar annotations are checked)",
                        "CSRF protection is turned off",
                        "No server-side session (stateless)",
                        "OAuth2 resource server: requests carry a bearer token (JWT or opaque)",
                        "Passwords are hashed with BCryptPasswordEncoder");
        assertThat(design.security().methodRules()).isEqualTo(1);
    }

    @Test
    void describesHowModelsAreUsed() {
        assertThat(design.ai().components())
                .containsExactly(new AiComponent("AssistantController", List.of("chat client")));
        assertThat(design.ai().tools())
                .containsExactly(
                        new AiTool("Tool", "cancel_order", "Cancels the order with this id", "OrderTools"),
                        new AiTool("Tool", "orderStatus", "", "OrderTools"));
        assertThat(design.ai().advisors()).containsExactly("SimpleLoggerAdvisor");
        assertThat(design.ai().vectorStores()).containsExactly("PgVectorStore", "pgvector");
        assertThat(design.ai().models())
                .containsExactly("Ollama embedding model: nomic-embed-text", "OpenAI chat model: gpt-5");
    }

    @Test
    void followsInjectedComponentsFromEachEntryPointAndLeavesOutData() {
        assertThat(design.flows())
                .containsExactlyInAnyOrder(
                        new Flow(
                                "OrderController",
                                "HTTP",
                                List.of("OrderService"),
                                List.of("OrderRepository", "PaymentClient")),
                        new Flow(
                                "OrderEvents",
                                "Message",
                                List.of("OrderService"),
                                List.of("OrderRepository", "PaymentClient")),
                        new Flow("AssistantController", "HTTP", List.of("OrderTools"), List.of("OrderService")));
    }

    @Test
    void skipsFilesThatCannotBeParsed() {
        SystemDesign broken = SampleProject.empty()
                .java("com/acme/Broken.java", "package com.acme; class Broken { void }")
                .java("com/acme/Fine.java", """
                        package com.acme;

                        @RestController
                        class Fine {
                            @GetMapping("/fine")
                            String fine() {
                                return "";
                            }
                        }
                        """)
                .design();

        assertThat(broken.endpoints()).extracting(Endpoint::path).containsExactly("/fine");
    }

    @Test
    void readsNestedTypesAndRecordsAsStoredTypes() {
        SystemDesign nested =
                SampleProject.empty().java("com/acme/Catalog.java", """
                        package com.acme;

                        class Catalog {

                            @Document(collection = "products")
                            record Product(@Id String sku, String name, @DBRef Supplier supplier) {}

                            @RestController
                            static class Api {
                                @QueryMapping
                                Product product() {
                                    return null;
                                }
                            }
                        }
                        """).design();

        assertThat(nested.entities())
                .containsExactly(new EntityModel(
                        "Product", "products", "sku", 3, List.of(new Relation("DBRef", "supplier", "Supplier"))));
        assertThat(nested.endpoints())
                .containsExactly(new Endpoint("QUERY", "product", "Api.product", "", "Product", ""));
    }
}
