package io.github.fereshtehdev.springinspector.rules.spring;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldInjectionCheckTest {

    private static final String FIXTURES = "fixtures/spring.field-injection/";

    private final FieldInjectionCheck check = new FieldInjectionCheck();

    private CheckTester with(String source) {
        return CheckTester.of(check).source(source);
    }

    @Test
    void identifiesItselfAsASpringBootRule() {
        assertThat(check.id()).isEqualTo(FieldInjectionCheck.ID);
        assertThat(check.category()).isEqualTo(Category.SPRING_BOOT);
    }

    @Test
    void reportsEveryInjectedField() {
        List<Finding> findings =
                with(resource(FIXTURES + "bad/InvoiceService.java")).findings();

        assertThat(findings)
                .extracting(finding -> finding.location().lines().start())
                .containsExactly(9, 12);
        assertThat(findings.getFirst().severity()).isEqualTo(Severity.MINOR);
        assertThat(findings.getFirst().confidence()).isEqualTo(Confidence.HIGH);
        assertThat(findings.getFirst().whatsWrong().description())
                .isEqualTo("Field invoices of InvoiceService is injected by field injection");
    }

    @Test
    void convertsAllInjectedFieldsToOneConstructor() {
        CheckTester bad = with(resource(FIXTURES + "bad/InvoiceService.java"));

        assertThat(bad.fixed(bad.findings().getFirst())).isEqualTo(resource(FIXTURES + "good/InvoiceService.java"));
    }

    @Test
    void theConvertedClassIsNoLongerReported() {
        CheckTester bad = with(resource(FIXTURES + "bad/InvoiceService.java"));

        assertThat(bad.afterFix(bad.findings().getLast()).findings()).isEmpty();
    }

    @Test
    void acceptsConstructorInjection() {
        assertThat(with(resource(FIXTURES + "good/InvoiceService.java")).findings())
                .isEmpty();
    }

    @Test
    void recognisesJakartaInject() {
        Finding finding = with("""
                        package com.acme;

                        import jakarta.inject.Inject;

                        class Report {
                            @Inject Clock clock;
                        }
                        """).singleFinding();

        assertThat(finding.howToFix().patch()).isPresent();
    }

    @Test
    void offersNoPatchWhenTheFixWouldChangeWhatIsInjected() {
        assertThat(with("""
                        package com.acme;

                        import org.springframework.beans.factory.annotation.Autowired;
                        import org.springframework.beans.factory.annotation.Qualifier;

                        class Optional {
                            @Autowired(required = false)
                            Clock clock;

                            @Autowired
                            @Qualifier("primary")
                            Store store;
                        }
                        """).findings())
                .hasSize(2)
                .allMatch(finding -> finding.howToFix().patch().isEmpty());
    }

    @Test
    void offersNoPatchWhenTheClassAlreadyHasOrGeneratesAConstructor() {
        assertThat(with("""
                        package com.acme;

                        import org.springframework.beans.factory.annotation.Autowired;

                        class Existing {
                            @Autowired Clock clock;

                            Existing() {}
                        }
                        """).singleFinding().howToFix().patch()).isEmpty();
        assertThat(with("""
                        package com.acme;

                        import lombok.RequiredArgsConstructor;
                        import org.springframework.beans.factory.annotation.Autowired;

                        @RequiredArgsConstructor
                        class Generated {
                            @Autowired Clock clock;
                        }
                        """).singleFinding().howToFix().patch()).isEmpty();
    }

    @Test
    void keepsTheImportWhileOtherCodeStillUsesIt() {
        CheckTester tester = with("""
                package com.acme;

                import org.springframework.beans.factory.annotation.Autowired;

                class Service {
                    @Autowired Clock clock;

                    @Autowired
                    void setStore(Store store) {}
                }
                """);

        assertThat(tester.fixed(tester.singleFinding()))
                .contains("import org.springframework.beans.factory.annotation.Autowired;")
                .contains("    Service(Clock clock) {\n        this.clock = clock;\n    }\n");
    }

    @Test
    void leavesTestCodeAlone() {
        String injectedTest = """
                package com.acme;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class ApplicationIT {
                    @Autowired Clock clock;
                }
                """;
        String testSource = """
                package com.acme;

                import org.springframework.beans.factory.annotation.Autowired;

                class Helper {
                    @Autowired Clock clock;
                }
                """;

        assertThat(with(injectedTest).findings()).isEmpty();
        assertThat(CheckTester.of(check)
                        .file("src/test/java/com/acme/Helper.java", testSource)
                        .findings())
                .isEmpty();
    }
}
