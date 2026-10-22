package io.github.fereshtehdev.springinspector.rules.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    /** A small project: its files go into both the change and the project map the rules read. */
    static final class Project {

        final Map<String, String> files = new LinkedHashMap<>();

        Project file(String path, String content) {
            files.put(path, content);
            return this;
        }

        ImportGraph graph() {
            List<JavaUnit> units = new ArrayList<>();
            files.forEach((path, content) -> units.add(JavaUnit.parse(Path.of(path), content)));
            return new ImportGraph(units);
        }

        List<Finding> findings(java.util.function.Function<ImportGraph, Check> rule) {
            CheckTester tester = CheckTester.of(rule.apply(graph()));
            files.forEach(tester::file);
            return tester.findings();
        }
    }

    static Project layered() {
        return new Project()
                .file("src/main/java/com/acme/web/OrderController.java", """
                        package com.acme.web;

                        import com.acme.repository.OrderRepository;
                        import com.acme.service.OrderService;

                        @RestController
                        class OrderController {}
                        """)
                .file(
                        "src/main/java/com/acme/web/OrderRequest.java",
                        "package com.acme.web;\nrecord OrderRequest() {}\n")
                .file("src/main/java/com/acme/service/OrderService.java", """
                        package com.acme.service;

                        import com.acme.repository.OrderRepository;
                        import com.acme.domain.Order;

                        @Service
                        public class OrderService {}
                        """)
                .file("src/main/java/com/acme/repository/OrderRepository.java", """
                        package com.acme.repository;

                        import com.acme.domain.Order;

                        public interface OrderRepository {}
                        """)
                .file(
                        "src/main/java/com/acme/domain/Order.java",
                        "package com.acme.domain;\n@Entity\npublic class Order {}\n");
    }

    @Nested
    class LayerViolations {

        @Test
        void reportsAControllerThatUsesARepositoryOnItsImport() {
            List<Finding> findings = layered().findings(graph -> new LayerViolationCheck(project -> graph));

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.ruleId().value()).isEqualTo("architecture.layer-violation");
                assertThat(finding.category()).isEqualTo(Category.ARCHITECTURE);
                assertThat(finding.severity()).isEqualTo(Severity.MINOR);
                assertThat(finding.location().file())
                        .isEqualTo(Path.of("src/main/java/com/acme/web/OrderController.java"));
                assertThat(finding.location().lines().start()).isEqualTo(3);
                assertThat(finding.whatsWrong().description())
                        .isEqualTo("OrderController uses the repository OrderRepository directly");
                assertThat(finding.whatsWrong().snippet()).isEqualTo("import com.acme.repository.OrderRepository;");
            });
        }

        @Test
        void reportsDependenciesAgainstTheDirectionOfTheLayers() {
            Project project = layered()
                    .file("src/main/java/com/acme/service/Billing.java", """
                            package com.acme.service;
                            import com.acme.web.OrderRequest;
                            public class Billing {}
                            """)
                    .file("src/main/java/com/acme/repository/Cache.java", """
                            package com.acme.repository;
                            import com.acme.service.*;
                            public class Cache {}
                            """)
                    .file("src/main/java/com/acme/domain/Invoice.java", """
                            package com.acme.domain;
                            import com.acme.repository.OrderRepository;
                            public class Invoice {}
                            """);

            List<Finding> findings = project.findings(graph -> new LayerViolationCheck(project1 -> graph));

            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .contains(
                            "Billing depends on the web class OrderRequest",
                            "Cache depends on the service class OrderService",
                            "The domain class Invoice depends on the repository class OrderRepository");
            assertThat(findings)
                    .filteredOn(finding -> finding.whatsWrong().description().startsWith("Billing"))
                    .allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(Severity.MAJOR));
        }

        @Test
        void staysQuietWithoutJavaInTheChangeOrWithoutAMap() {
            CheckTester withoutJava = CheckTester.of(
                            new LayerViolationCheck(project -> layered().graph()))
                    .file("pom.xml", "<project/>");

            assertThat(withoutJava.findings()).isEmpty();
            assertThat(CheckTester.of(new LayerViolationCheck())
                            .file("src/A.java", "class A {}")
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class Cycles {

        @Test
        void reportsEachImportThatClosesACycleBetweenTwoPackages() {
            Project project = new Project()
                    .file("src/main/java/com/acme/orders/Checkout.java", """
                            package com.acme.orders;

                            import com.acme.billing.Invoices;

                            public class Checkout {}
                            """)
                    .file("src/main/java/com/acme/billing/Invoices.java", """
                            package com.acme.billing;
                            import com.acme.orders.Checkout;
                            public class Invoices {}
                            """);

            List<Finding> findings = project.findings(graph -> new PackageCycleCheck(context -> graph));

            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .containsExactlyInAnyOrder(
                            "Packages com.acme.orders and com.acme.billing depend on each other",
                            "Packages com.acme.billing and com.acme.orders depend on each other");
            assertThat(findings)
                    .allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(Severity.MINOR));
        }

        @Test
        void describesLongerCyclesAndIgnoresDependenciesInOneDirection() {
            Project project = new Project();
            for (int i = 0; i < 7; i++) {
                project.file(
                        "src/p" + i + "/T" + i + ".java",
                        "package p" + i + ";\nimport p" + ((i + 1) % 7) + ".T" + ((i + 1) % 7) + ";\nclass T" + i
                                + " {}\n");
            }
            project.file("src/lone/Lone.java", "package lone;\nimport p0.T0;\nclass Lone {}\n");

            List<Finding> findings = project.findings(graph -> new PackageCycleCheck(context -> graph));

            assertThat(findings).hasSize(7);
            assertThat(findings.getFirst().whatsWrong().description())
                    .contains("dependency cycle of 7 packages")
                    .endsWith("and 2 more");
            assertThat(findings)
                    .noneMatch(finding -> finding.location().file().toString().contains("Lone"));
        }
    }

    @Nested
    class DomainFrameworks {

        static Project hexagonal(String domainImport) {
            return new Project()
                    .file("src/main/java/com/acme/domain/Account.java", """
                            package com.acme.domain;

                            import java.math.BigDecimal;
                            import %s;

                            public class Account {}
                            """.formatted(domainImport))
                    .file("src/main/java/com/acme/adapter/AccountEntity.java", """
                            package com.acme.adapter;
                            import jakarta.persistence.Entity;
                            import com.acme.domain.Account;
                            public class AccountEntity {}
                            """);
        }

        @Test
        void reportsFrameworkImportsInTheDomainOfAHexagonalProject() {
            List<Finding> findings = hexagonal("jakarta.persistence.Entity")
                    .findings(graph -> new DomainFrameworkCheck(context -> graph));

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.whatsWrong().description()).isEqualTo("The domain class Account depends on JPA");
                assertThat(finding.severity()).isEqualTo(Severity.MAJOR);
                assertThat(finding.location().lines().start()).isEqualTo(4);
            });
            assertThat(hexagonal("org.springframework.stereotype.Component")
                            .findings(graph -> new DomainFrameworkCheck(context -> graph)))
                    .singleElement()
                    .satisfies(finding ->
                            assertThat(finding.whatsWrong().description()).endsWith("depends on Spring"));
        }

        @Test
        void acceptsPlainDomainsAndLayeredProjects() {
            assertThat(hexagonal("java.time.Instant").findings(graph -> new DomainFrameworkCheck(context -> graph)))
                    .isEmpty();
            assertThat(new Project()
                            .file("src/main/java/com/acme/domain/Order.java", """
                                    package com.acme.domain;
                                    import jakarta.persistence.Entity;
                                    @Entity class Order {}
                                    """)
                            .findings(graph -> new DomainFrameworkCheck(context -> graph)))
                    .isEmpty();
            assertThat(new DomainFrameworkCheck().id().value()).isEqualTo("architecture.domain-depends-on-framework");
        }
    }

    @Test
    void classifiesLayersByStereotypeNameAndPackage() {
        assertThat(Layer.of(JavaUnit.parse(Path.of("Billing.java"), "package x;\n@Service class Billing {}\n")))
                .isEqualTo(Layer.SERVICE);
        assertThat(Layer.of(JavaUnit.parse(Path.of("OrderDao.java"), "package x;\nclass OrderDao {}\n")))
                .isEqualTo(Layer.REPOSITORY);
        assertThat(Layer.of(JavaUnit.parse(Path.of("Api.java"), "package com.acme.rest;\nclass Api {}\n")))
                .isEqualTo(Layer.WEB);
        assertThat(Layer.of(JavaUnit.parse(Path.of("App.java"), "package x;\n@SpringBootApplication class App {}\n")))
                .isEqualTo(Layer.CONFIG);
        assertThat(Layer.of(JavaUnit.parse(Path.of("Item.java"), "package com.acme.model;\nclass Item {}\n")))
                .isEqualTo(Layer.DOMAIN);
        assertThat(Layer.of(JavaUnit.parse(Path.of("Util.java"), "package com.acme.util;\nclass Util {}\n")))
                .isEqualTo(Layer.OTHER);
        assertThat(Layer.ofPackage("com.acme.config")).isEqualTo(Layer.CONFIG);
        assertThat(Layer.WEB.label()).isEqualTo("web");
    }
}
