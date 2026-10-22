package io.github.fereshtehdev.springinspector.rules.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureReport.StyleMatch;
import io.github.fereshtehdev.springinspector.rules.architecture.ArchitectureStylesTest.Sample;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchitectureEdgeCasesTest {

    @Test
    void reportsMediumConfidenceForWeakerEvidence() {
        ArchitectureReport report = new Sample()
                .type("com.a.web.Home")
                .type("com.a.web.Login")
                .type("com.a.service.Users", "com.a.web.Home")
                .type("com.a.service.Roles")
                .type("com.a.orders.Order", "com.a.billing.Bill")
                .type("com.a.orders.Line")
                .type("com.a.billing.Bill", "com.a.orders.Order")
                .type("com.a.billing.Tax")
                .type("com.a.domain.Money")
                .type("com.a.adapter.Store", "com.a.domain.Money")
                .type("com.a.command.Create")
                .type("com.a.query.Find")
                .type("com.a.events.Publish", "org.springframework.context.ApplicationEventPublisher")
                .report(0);

        assertThat(confidence(report, ArchitectureStyle.LAYERED)).isEqualTo("high");
        assertThat(confidence(
                        new Sample()
                                .type("com.b.web.Home")
                                .type("com.b.service.Users")
                                .report(0),
                        ArchitectureStyle.LAYERED))
                .isEqualTo("medium");
        assertThat(confidence(report, ArchitectureStyle.PACKAGE_BY_FEATURE)).isEqualTo("medium");
        assertThat(confidence(report, ArchitectureStyle.HEXAGONAL)).isEqualTo("medium");
        assertThat(confidence(report, ArchitectureStyle.CQRS)).isEqualTo("medium");
        assertThat(confidence(report, ArchitectureStyle.EVENT_DRIVEN)).isEqualTo("medium");
        assertThat(report.diagram()).contains("linkStyle");
    }

    @Test
    void namesEveryFeatureEvenInLargeLayeredProjects() {
        Sample sample = new Sample();
        for (String name : List.of("Order", "Customer", "Product", "Invoice", "Payment", "Shipment", "Review")) {
            sample.type("com.big.web." + name + "Controller", "com.big.service." + name + "Service")
                    .type("com.big.service." + name + "Service", "com.big.repository." + name + "Repository")
                    .type("com.big.repository." + name + "Repository");
        }

        ArchitectureReport report = sample.report(0);

        assertThat(ArchitectureStylesTest.suggestion(report, "Organise the code by feature")
                        .steps())
                .anySatisfy(step -> assertThat(step).startsWith("  and 2 more features: Review, Shipment"));
        assertThat(report.rootPackage()).isEqualTo("com.big");
    }

    @Test
    void describesThreePackageCyclesAndTheirCheapestLink() {
        Sample sample = new Sample()
                .type("com.c.a.A1", "com.c.b.B1")
                .type("com.c.a.A2", "com.c.b.B1")
                .type("com.c.b.B1", "com.c.c.C1")
                .type("com.c.c.C1", "com.c.a.A1");

        ArchitectureReport report = sample.report(0);

        assertThat(report.cycles())
                .singleElement()
                .satisfies(cycle -> assertThat(cycle).containsExactly("a", "b", "c"));
        assertThat(ArchitectureStylesTest.suggestion(report, "Break the cycle between a, b, c")
                        .steps()
                        .getFirst())
                .contains("used only by");
        assertThat(sample.tester(new PackageCycleCheck(project -> sample.graph()))
                        .findings())
                .allSatisfy(finding -> assertThat(finding.whatsWrong().description())
                        .contains("dependency cycle of 3 packages: com.c.a, com.c.b, com.c.c"));
    }

    @Test
    void handlesProjectsWithoutACommonRootOrPackage() {
        ArchitectureReport report = new Sample()
                .type("org.one.Alpha", "net.two.Beta")
                .type("net.two.Beta")
                .report(0);
        ImportGraph defaultPackage =
                new ImportGraph(List.of(io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit.parse(
                        java.nio.file.Path.of("Main.java"), "class Main {}\n")));

        assertThat(report.rootPackage()).isEmpty();
        assertThat(report.parts()).extracting(ArchitectureReport.Part::name).containsExactly("net", "org");
        assertThat(ArchitectureReport.of(defaultPackage, 0).parts()).isEmpty();
        assertThat(ArchitectureReport.relative(new Architecture(defaultPackage), "x.y"))
                .isEqualTo("x.y");
    }

    @Test
    void appliesFeatureBoundariesToModulithProjectsWithSeveralModules() {
        Sample sample = ArchitectureStylesTest.byFeature()
                .type("com.shop.ModulithTest", "org.springframework.modulith.core.ApplicationModules");

        List<StyleMatch> styles = sample.report(2).styles();

        assertThat(FeatureInternalsCheck.appliesTo(styles)).isTrue();
        assertThat(sample.tester(new FeatureInternalsCheck(project -> sample.graph()))
                        .findings())
                .hasSize(1);
    }

    @Test
    void readsEveryLayerNameInDeclaredRules() {
        assertThat(DeclaredLayers.read(new RuleOptions(Map.of("service", "repository config other domain web"))))
                .containsKey(Layer.SERVICE);
        assertThat(Layer.ofPackage("com.acme.configuration")).isEqualTo(Layer.CONFIG);
        assertThat(Layer.ofPackage("com.acme.dao")).isEqualTo(Layer.REPOSITORY);
        assertThat(Packages.segments("")).isEmpty();
    }

    @Test
    void staysQuietWithoutJavaInTheChange() {
        Sample sample = ArchitectureStylesTest.byFeature();

        for (var check : List.of(
                new PackageCycleCheck(project -> sample.graph()),
                new DomainFrameworkCheck(project -> sample.graph()),
                new FeatureInternalsCheck(project -> sample.graph()),
                new NewFeatureDependencyCheck(project -> sample.graph()))) {
            assertThat(CheckTester.of(check).file("pom.xml", "<project/>").findings())
                    .isEmpty();
        }
        assertThat(new FeatureInternalsCheck().id().value()).isEqualTo("architecture.feature-internals");
        assertThat(new PackageCycleCheck().id().value()).isEqualTo("architecture.package-cycle");
    }

    private static String confidence(ArchitectureReport report, ArchitectureStyle style) {
        return report.styles().stream()
                .filter(match -> match.style() == style)
                .findFirst()
                .orElseThrow(() -> new AssertionError(style + " not recognised in " + report.styles()))
                .confidence();
    }
}
