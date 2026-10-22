package io.github.fereshtehdev.springinspector.rules.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.Suggestion;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArchitectureStylesTest {

    /** Classes of a sample project by qualified name, with their imports and annotations. */
    static final class Sample {

        final Map<String, String> files = new LinkedHashMap<>();

        Sample type(String qualifiedName, String... importsAndAnnotations) {
            int dot = qualifiedName.lastIndexOf('.');
            String packageName = qualifiedName.substring(0, dot);
            String type = qualifiedName.substring(dot + 1);
            StringBuilder source = new StringBuilder("package " + packageName + ";\n\n");
            List<String> annotations = new ArrayList<>();
            for (String item : importsAndAnnotations) {
                if (item.startsWith("@")) {
                    annotations.add(item);
                } else {
                    source.append("import ").append(item).append(";\n");
                }
            }
            source.append('\n');
            annotations.forEach(annotation -> source.append(annotation).append('\n'));
            source.append("public class ").append(type).append(" {}\n");
            files.put("src/main/java/" + qualifiedName.replace('.', '/') + ".java", source.toString());
            return this;
        }

        String source(String path) {
            return java.util.Objects.requireNonNull(files.get(path), path);
        }

        ImportGraph graph() {
            List<JavaUnit> units = new ArrayList<>();
            files.forEach((path, content) -> units.add(JavaUnit.parse(Path.of(path), content)));
            return new ImportGraph(units);
        }

        ArchitectureReport report(int buildModules) {
            return ArchitectureReport.of(graph(), buildModules);
        }

        CheckTester tester(io.github.fereshtehdev.springinspector.core.port.Check check) {
            CheckTester tester = CheckTester.of(check);
            files.forEach(tester::file);
            return tester;
        }
    }

    static Sample layered() {
        Sample sample = new Sample().type("com.shop.ShopApplication", "@SpringBootApplication");
        for (String name : List.of("Order", "Customer", "Product")) {
            sample.type(
                            "com.shop.controller." + name + "Controller",
                            "com.shop.service." + name + "Service",
                            "@RestController")
                    .type(
                            "com.shop.service." + name + "Service",
                            "com.shop.repository." + name + "Repository",
                            "@Service")
                    .type("com.shop.repository." + name + "Repository", "com.shop.domain." + name)
                    .type("com.shop.domain." + name, "@Entity");
        }
        return sample;
    }

    static Sample byFeature() {
        return new Sample()
                .type("com.shop.ShopApplication", "@SpringBootApplication")
                .type("com.shop.orders.OrderController", "com.shop.orders.OrderService", "@RestController")
                .type("com.shop.orders.OrderService", "com.shop.billing.internal.TaxTable", "@Service")
                .type("com.shop.billing.BillingService", "com.shop.billing.internal.TaxTable", "@Service")
                .type("com.shop.billing.internal.TaxTable")
                .type("com.shop.customers.CustomerService", "@Service")
                .type("com.shop.customers.Customer", "@Entity");
    }

    @Nested
    class Styles {

        @Test
        void recognisesALayeredApplicationAndSuggestsPackagesByFeature() {
            ArchitectureReport report = layered().report(0);

            assertThat(report.follows(ArchitectureStyle.LAYERED)).isTrue();
            assertThat(report.follows(ArchitectureStyle.PACKAGE_BY_FEATURE)).isFalse();
            assertThat(report.rootPackage()).isEqualTo("com.shop");
            assertThat(report.styles().getFirst().confidence()).isEqualTo("high");
            Suggestion byFeature = suggestion(report, "Organise the code by feature");
            assertThat(byFeature.why()).contains("3 business features (Customer, Order, Product)");
            assertThat(byFeature.steps())
                    .anySatisfy(step -> assertThat(step)
                            .contains("com.shop.order: Order, OrderController, OrderRepository, OrderService"));
            assertThat(report.parts()).extracting(ArchitectureReport.Part::name).contains("controller", "service");
        }

        @Test
        void recognisesPackagesByFeatureAndTheirBoundaries() {
            ArchitectureReport report = byFeature().report(0);

            assertThat(report.follows(ArchitectureStyle.PACKAGE_BY_FEATURE)).isTrue();
            assertThat(report.parts())
                    .extracting(ArchitectureReport.Part::name)
                    .containsExactly("billing", "customers", "orders");
            assertThat(report.parts().get(2).dependsOn()).containsEntry("billing", 1);
            assertThat(suggestion(report, "Make feature boundaries explicit with Spring Modulith")
                            .steps())
                    .anySatisfy(
                            step -> assertThat(step).contains("OrderService (orders) → TaxTable (billing.internal)"));
            assertThat(report.diagram())
                    .startsWith("flowchart LR")
                    .contains("orders<br/>2 classes")
                    .contains("-->|1|");
        }

        @Test
        void recognisesHexagonalCleanModulithCqrsEventsAndSeveralApplications() {
            Sample sample = new Sample()
                    .type("com.bank.domain.Account", "org.springframework.stereotype.Component")
                    .type("com.bank.domain.port.AccountStore")
                    .type("com.bank.adapter.JpaAccountStore", "com.bank.domain.port.AccountStore")
                    .type("com.bank.orders.CreateOrderCommand")
                    .type("com.bank.orders.CancelOrderCommand")
                    .type("com.bank.orders.FindOrderQuery")
                    .type("com.bank.orders.ListOrdersQuery")
                    .type("com.bank.orders.OrderEvents", "org.springframework.context.ApplicationEventPublisher")
                    .type("com.bank.orders.Listener", "org.springframework.kafka.annotation.KafkaListener")
                    .type("com.bank.orders.Modules", "org.springframework.modulith.core.ApplicationModules")
                    .type("com.bank.api.ApiApplication", "@SpringBootApplication")
                    .type("com.bank.worker.WorkerApplication", "@SpringBootApplication");

            ArchitectureReport report = sample.report(3);

            assertThat(report.styles())
                    .extracting(ArchitectureReport.StyleMatch::style)
                    .contains(
                            ArchitectureStyle.HEXAGONAL,
                            ArchitectureStyle.SPRING_MODULITH,
                            ArchitectureStyle.CQRS,
                            ArchitectureStyle.EVENT_DRIVEN,
                            ArchitectureStyle.MULTI_MODULE,
                            ArchitectureStyle.MULTIPLE_APPLICATIONS);
            assertThat(suggestion(report, "Keep the domain free of frameworks").steps())
                    .anySatisfy(step -> assertThat(step).contains("Account uses Spring"));
            assertThat(report.rootPackage()).isEqualTo("com.bank");
        }

        @Test
        void recognisesCleanArchitecture() {
            ArchitectureReport report = new Sample()
                    .type("com.app.domain.Invoice")
                    .type("com.app.usecase.PayInvoice", "com.app.domain.Invoice")
                    .type("com.app.infrastructure.InvoiceTable", "com.app.domain.Invoice", "jakarta.persistence.Entity")
                    .report(0);

            assertThat(report.follows(ArchitectureStyle.CLEAN)).isTrue();
            assertThat(report.follows(ArchitectureStyle.HEXAGONAL)).isFalse();
            assertThat(report.suggestions()).isEmpty();
        }

        @Test
        void suggestsAStructureForALargeUnstructuredProjectAndSplittingHotspots() {
            Sample sample = new Sample();
            List<String> helpers = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                helpers.add("x.Helper" + i);
            }
            sample.type("x.Hub", helpers.toArray(String[]::new));
            for (int i = 0; i < 12; i++) {
                sample.type("x.Helper" + i);
                sample.type("x.User" + i, "x.Hub");
            }

            ArchitectureReport report = sample.report(0);

            assertThat(report.styles()).isEmpty();
            assertThat(suggestion(report, "Choose a structure for the code")).isNotNull();
            assertThat(report.hotspots()).singleElement().satisfies(hotspot -> {
                assertThat(hotspot.type()).isEqualTo("x.Hub");
                assertThat(hotspot.usedBy()).isEqualTo(12);
                assertThat(hotspot.uses()).isEqualTo(12);
            });
            assertThat(suggestion(report, "Split classes that do too much")
                            .steps()
                            .getFirst())
                    .isEqualTo("x.Hub: used by 12 classes, uses 12");
        }

        @Test
        void suggestsOneStructureWhenLayersAndFeaturesAreMixed() {
            Sample sample = byFeature()
                    .type("com.shop.service.Mailer", "@Service")
                    .type("com.shop.service.Pricing", "@Service")
                    .type("com.shop.controller.HealthController", "@RestController")
                    .type(
                            "com.shop.controller.AdminController",
                            "com.shop.repository.AuditRepository",
                            "@RestController")
                    .type("com.shop.repository.AuditRepository");

            ArchitectureReport report = sample.report(0);

            assertThat(suggestion(report, "Use one structure at the top level").why())
                    .contains("controller, repository, service")
                    .contains("billing, customers, orders");
            assertThat(suggestion(report, "Route data access through services")
                            .steps()
                            .getFirst())
                    .contains("AdminController → AuditRepository");
        }
    }

    @Nested
    class Rules {

        @Test
        void reportsFeaturesThatReachIntoAnotherFeature() {
            List<Finding> findings = byFeature()
                    .tester(new FeatureInternalsCheck(project -> byFeature().graph()))
                    .findings();

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.ruleId().value()).isEqualTo("architecture.feature-internals");
                assertThat(finding.whatsWrong().description())
                        .isEqualTo(
                                "OrderService (orders) uses TaxTable from billing.internal, inside the billing feature");
                assertThat(finding.howToFix().explanation()).startsWith("Use a class in billing instead");
            });
        }

        @Test
        void leavesSubPackagesOfMultiModuleBuildsAlone() {
            ArchitectureReport report = byFeature().report(4);

            assertThat(FeatureInternalsCheck.appliesTo(report.styles())).isFalse();
            assertThat(report.suggestions())
                    .extracting(Suggestion::title)
                    .doesNotContain("Make feature boundaries explicit with Spring Modulith");
        }

        @Test
        void reportsAFeatureDependencyThatOnlyThisChangeCreates() {
            Sample project = byFeature().type("com.shop.customers.Loyalty", "com.shop.orders.OrderService");
            String changed = "src/main/java/com/shop/customers/Loyalty.java";

            List<Finding> findings = CheckTester.of(new NewFeatureDependencyCheck(context -> project.graph()))
                    .file(changed, project.source(changed))
                    .findings();

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.whatsWrong().description())
                        .isEqualTo("This change makes customers depend on orders for the first time");
                assertThat(finding.severity()).isEqualTo(Severity.INFO);
            });
        }

        @Test
        void staysQuietForExistingDependenciesAndWholeProjectRuns() {
            Sample project = byFeature();
            String changed = "src/main/java/com/shop/orders/OrderService.java";

            assertThat(CheckTester.of(new NewFeatureDependencyCheck(context -> project.graph()))
                            .file(changed, project.source(changed))
                            .findings())
                    .hasSize(1);
            assertThat(project.tester(new NewFeatureDependencyCheck(context -> project.graph()))
                            .findings())
                    .isEmpty();
            assertThat(new NewFeatureDependencyCheck().id().value()).isEqualTo("architecture.new-feature-dependency");
        }

        @Test
        void followsTheLayersTheTeamDeclares() {
            Sample project = layered();
            CheckTester tester = project.tester(new LayerViolationCheck(context -> project.graph()))
                    .option("web", "repository, domain");

            List<Finding> findings = tester.findings();

            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .contains("OrderController (web) uses OrderService (service), which the project's layer rules do"
                            + " not allow");
            assertThat(findings.getFirst().whyItMatters().explanation()).contains("only: repository, domain");
        }

        @Test
        void rejectsLayersItDoesNotKnow() {
            Sample project = layered();

            assertThatThrownBy(() -> DeclaredLayers.read(
                            new io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions(
                                    Map.of("web", "services"))))
                    .hasMessageContaining("'services' is not a layer");
            assertThat(project.tester(new LayerViolationCheck(context -> project.graph()))
                            .option("domain", "")
                            .findings())
                    .isEmpty();
        }
    }

    static Suggestion suggestion(ArchitectureReport report, String title) {
        return report.suggestions().stream()
                .filter(suggestion -> suggestion.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No suggestion '" + title + "' in " + report.suggestions()));
    }
}
