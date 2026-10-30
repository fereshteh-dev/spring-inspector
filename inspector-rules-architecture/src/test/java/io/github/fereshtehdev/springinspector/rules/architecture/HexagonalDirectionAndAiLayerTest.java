package io.github.fereshtehdev.springinspector.rules.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureRulesTest.Project;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HexagonalDirectionAndAiLayerTest {

    private static final String ROOT = "src/main/java/com/acme/";

    private static String type(String packageName, String name, String... imports) {
        StringBuilder source = new StringBuilder("package com.acme." + packageName + ";\n\n");
        for (String imported : imports) {
            source.append("import com.acme.").append(imported).append(";\n");
        }
        return source.append("\npublic class ").append(name).append(" {}\n").toString();
    }

    private static Project hexagon() {
        return new Project()
                .file(ROOT + "domain/Order.java", type("domain", "Order"))
                .file(ROOT + "application/port/out/SaveOrder.java", type("application.port.out", "SaveOrder"))
                .file(ROOT + "adapter/out/jpa/JpaOrders.java", type("adapter.out.jpa", "JpaOrders"))
                .file(ROOT + "adapter/out/mail/Mailer.java", type("adapter.out.mail", "Mailer"))
                .file(ROOT + "adapter/shared/Clock.java", type("adapter.shared", "Clock"));
    }

    private static List<Finding> direction(Project project) {
        return project.findings(graph -> new HexagonalDirectionCheck(context -> graph));
    }

    @Nested
    class HexagonalDirection {

        @Test
        void reportsTheCoreUsingAnAdapter() {
            Finding finding = direction(hexagon()
                            .file(
                                    ROOT + "application/PlaceOrder.java",
                                    type("application", "PlaceOrder", "adapter.out.jpa.JpaOrders")))
                    .getFirst();

            assertThat(finding.severity()).isEqualTo(Severity.MAJOR);
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("The core class PlaceOrder depends on the adapter JpaOrders");
        }

        @Test
        void reportsOneAdapterUsingAnother() {
            Finding finding = direction(hexagon()
                            .file(
                                    ROOT + "adapter/in/web/Orders.java",
                                    type("adapter.in.web", "Orders", "adapter.out.jpa.JpaOrders")))
                    .getFirst();

            assertThat(finding.severity()).isEqualTo(Severity.MINOR);
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("The in.web adapter class Orders uses JpaOrders from the out.jpa adapter");
        }

        @Test
        void acceptsAdaptersUsingTheCoreSharedCodeAndTheirOwnClasses() {
            assertThat(direction(hexagon()
                            .file(
                                    ROOT + "adapter/out/jpa/OrderMapper.java",
                                    type(
                                            "adapter.out.jpa",
                                            "OrderMapper",
                                            "domain.Order",
                                            "application.port.out.SaveOrder",
                                            "adapter.shared.Clock"))
                            .file(
                                    ROOT + "adapter/out/jpa/mapping/Rows.java",
                                    type("adapter.out.jpa.mapping", "Rows", "adapter.out.jpa.JpaOrders"))))
                    .isEmpty();
        }

        @Test
        void leavesImportsThatBreakTheLayersToTheLayerRule() {
            assertThat(direction(hexagon()
                            .file(
                                    ROOT + "adapter/in/web/OrderController.java",
                                    type("adapter.in.web", "OrderController"))
                            .file(
                                    ROOT + "domain/Invoice.java",
                                    type("domain", "Invoice", "adapter.in.web.OrderController"))))
                    .isEmpty();
        }

        @Test
        void doesNotApplyToProjectsThatAreNotHexagonal() {
            assertThat(direction(new Project()
                            .file(ROOT + "billing/Invoice.java", type("billing", "Invoice", "shipping.Parcel"))
                            .file(ROOT + "shipping/Parcel.java", type("shipping", "Parcel"))))
                    .isEmpty();
        }
    }

    @Nested
    class AiInWebLayer {

        private static final String CONTROLLER = """
                package com.acme.web;

                import org.springframework.ai.chat.client.ChatClient;

                @RestController
                class AskController {}
                """;

        private static final String SERVICE = """
                package com.acme.service;

                import org.springframework.ai.chat.client.ChatClient;

                @Service
                class AskService {}
                """;

        @Test
        void reportsAControllerThatUsesAModelClient() {
            List<Finding> findings = new Project()
                    .file(ROOT + "web/AskController.java", CONTROLLER)
                    .findings(graph -> new AiInWebLayerCheck(context -> graph));

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.severity()).isEqualTo(Severity.MINOR);
                assertThat(finding.whatsWrong().description())
                        .isEqualTo("AskController uses ChatClient directly from the web layer");
                assertThat(finding.location().lines().start()).isEqualTo(3);
            });
        }

        @Test
        void acceptsAServiceThatUsesAModelClient() {
            assertThat(new Project()
                            .file(ROOT + "service/AskService.java", SERVICE)
                            .findings(graph -> new AiInWebLayerCheck(context -> graph)))
                    .isEmpty();
        }
    }
}
