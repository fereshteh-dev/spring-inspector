package io.github.fereshtehdev.springinspector.rules.spring;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpringBootRulesTest {

    private static String fixture(String rule, String path) {
        return resource("fixtures/" + rule + "/" + path);
    }

    private static String entity() {
        return fixture("spring.entity-in-controller", "bad/Order.java");
    }

    private static List<Integer> lines(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.location().lines().start())
                .toList();
    }

    private static void assertSpringBootRule(Check check, String id) {
        assertThat(check.id().value()).isEqualTo(id);
        assertThat(check.category()).isEqualTo(Category.SPRING_BOOT);
        assertThat(check).isInstanceOf(JavaCheck.class);
    }

    @Nested
    class AutowiredConstructor {

        private static final String RULE = "spring.autowired-constructor";
        private final AutowiredConstructorCheck check = new AutowiredConstructorCheck();

        @Test
        void removesTheAnnotationAndItsImport() {
            CheckTester bad = CheckTester.of(check).source(fixture(RULE, "bad/OrderService.java"));

            assertSpringBootRule(check, RULE);
            assertThat(bad.fixed(bad.singleFinding())).isEqualTo(fixture(RULE, "good/OrderService.java"));
        }

        @Test
        void keepsAnnotationsThatChooseBetweenConstructors() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import org.springframework.beans.factory.annotation.Autowired;

                                    class Two {
                                        @Autowired
                                        Two(String a) {}

                                        Two() {}
                                    }
                                    """).findings()).isEmpty();
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/OrderService.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class TransactionalNotProxied {

        private static final String RULE = "spring.transactional-not-proxied";
        private final TransactionalNotProxiedCheck check = new TransactionalNotProxiedCheck();

        @Test
        void reportsPrivateFinalAndStaticMethodsAndFinalClasses() {
            String bad = fixture(RULE, "bad/Billing.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "void charge"),
                            lineOf(bad, "void refund"),
                            lineOf(bad, "void archive"),
                            lineOf(bad, "class Ledger"));
            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .contains("@Transactional on private method charge is ignored");
        }

        @Test
        void acceptsMethodsSpringCanProxy() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Billing.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class TransactionalSelfInvocation {

        private static final String RULE = "spring.transactional-self-invocation";
        private final TransactionalSelfInvocationCheck check = new TransactionalSelfInvocationCheck();

        @Test
        void reportsCallsThatBypassTheProxy() {
            String bad = fixture(RULE, "bad/Checkout.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertSpringBootRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "this.saveOrder"));
            assertThat(finding.whatsWrong().description())
                    .isEqualTo(
                            "placeOrder() calls @Transactional saveOrder() on the same object, so no transaction starts");
        }

        @Test
        void acceptsTransactionalCallersAndCallsThroughOtherBeans() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Checkout.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class TransactionalReadOnly {

        private static final String RULE = "spring.transactional-read-only";
        private final TransactionalReadOnlyCheck check = new TransactionalReadOnlyCheck();

        @Test
        void reportsQueryMethodsAndMarksThemReadOnly() {
            String bad = fixture(RULE, "bad/CustomerService.java");
            CheckTester tester = CheckTester.of(check).source(bad);

            List<Finding> findings = tester.findings();

            assertSpringBootRule(check, RULE);
            assertThat(findings).hasSize(2).allMatch(finding -> finding.severity() == Severity.MINOR);
            assertThat(tester.fixed(findings.getFirst()))
                    .contains("@Transactional(readOnly = true)\n    public List<Customer> findByCountry");
        }

        @Test
        void acceptsReadOnlyTransactionsWritesAndReadThenWriteMethods() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/CustomerService.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class EntityInController {

        private static final String RULE = "spring.entity-in-controller";
        private final EntityInControllerCheck check = new EntityInControllerCheck();

        @Test
        void reportsEntitiesReturnedDirectlyOrInsideContainers() {
            String bad = fixture(RULE, "bad/OrderController.java");

            List<Finding> findings =
                    CheckTester.of(check).source(bad).source(entity()).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactly(lineOf(bad, "Order get("), lineOf(bad, "ResponseEntity<List<Order>> all()"));
            assertThat(findings.getLast().whatsWrong().description())
                    .isEqualTo("all returns the entity Order to the client");
        }

        @Test
        void acceptsResponseRecordsAndNonHandlerMethods() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/OrderController.java"))
                            .source(entity())
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class MissingValid {

        private static final String RULE = "spring.missing-valid";
        private final MissingValidCheck check = new MissingValidCheck();

        @Test
        void addsValidWhenTheRequestTypeHasConstraints() {
            CheckTester bad = CheckTester.of(check)
                    .source(fixture(RULE, "bad/SignupController.java"))
                    .source(fixture(RULE, "bad/SignupRequest.java"));

            Finding finding = bad.singleFinding();

            assertSpringBootRule(check, RULE);
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("The constraints on SignupRequest are not checked: @Valid is missing");
            assertThat(bad.fixed(finding))
                    .contains(
                            "import jakarta.validation.Valid;\nimport org.springframework.web.bind.annotation.PostMapping;")
                    .contains("void signup(@Valid @RequestBody SignupRequest request)");
        }

        @Test
        void acceptsValidatedBodiesAndTypesWithoutConstraints() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/SignupController.java"))
                            .source(fixture(RULE, "bad/SignupRequest.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class FindAllWithoutPaging {

        private static final String RULE = "spring.find-all-unpaged";
        private final FindAllWithoutPagingCheck check = new FindAllWithoutPagingCheck();

        @Test
        void reportsUnpagedFindAllInControllers() {
            String bad = fixture(RULE, "bad/ProductController.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertSpringBootRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "productRepository.findAll()"));
        }

        @Test
        void acceptsPagedQueries() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/ProductController.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class NPlusOne {

        private static final String RULE = "spring.n-plus-one";
        private final NPlusOneCheck check = new NPlusOneCheck();

        @Test
        void reportsLazyAssociationsReadInLoopsOverRepositoryResults() {
            String bad = fixture(RULE, "bad/InvoiceService.java");

            List<Finding> findings =
                    CheckTester.of(check).source(bad).source(entity()).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactly(lineOf(bad, "order.getLines()"), lineOf(bad, "order.getCustomer()"));
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("order.getLines() loads lines once per loop iteration (N+1 queries)");
        }

        @Test
        void acceptsPlainColumnsAndCollectionsNotLoadedHere() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/InvoiceService.java"))
                            .source(entity())
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class BlockingInReactive {

        private static final String RULE = "spring.blocking-in-reactive";
        private final BlockingInReactiveCheck check = new BlockingInReactiveCheck();

        @Test
        void reportsBlockSleepAndJdbcInReactiveMethods() {
            String bad = fixture(RULE, "bad/QuoteHandler.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactly(
                            lineOf(bad, ".block()"), lineOf(bad, "Thread.sleep"), lineOf(bad, "jdbc.queryForObject"));
            assertThat(findings.getLast().whatsWrong().description())
                    .isEqualTo("jdbc.queryForObject() blocks inside reactive method quote");
        }

        @Test
        void acceptsComposedReactiveCodeAndBlockingOutsideIt() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/QuoteHandler.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class VirtualThreadPinning {

        private static final String RULE = "spring.virtual-thread-pinning";
        private final VirtualThreadPinningCheck check = new VirtualThreadPinningCheck();

        @Test
        void appliesOnlyToJava21To23() {
            assertSpringBootRule(check, RULE);
            assertThat(CheckTester.of(check).javaVersion("21").applies()).isTrue();
            assertThat(CheckTester.of(check).javaVersion("23").applies()).isTrue();
            assertThat(CheckTester.of(check).javaVersion("24").applies()).isFalse();
            assertThat(CheckTester.of(check).javaVersion("17").applies()).isFalse();
        }

        @Test
        void reportsBlockingCallsInsideSynchronized() {
            String bad = fixture(RULE, "bad/RateCache.java");

            List<Finding> findings =
                    CheckTester.of(check).javaVersion("21").source(bad).findings();

            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(lineOf(bad, "rates.get()"), lineOf(bad, "Thread.sleep"));
        }

        @Test
        void acceptsLocksAndSynchronizedWithoutBlocking() {
            assertThat(CheckTester.of(check)
                            .javaVersion("21")
                            .source(fixture(RULE, "good/RateCache.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class RestTemplate {

        private static final String RULE = "spring.rest-template";
        private final RestTemplateCheck check = new RestTemplateCheck();

        @Test
        void hintsAtRestClientForFieldsAndNewInstances() {
            String bad = fixture(RULE, "bad/WeatherClient.java");

            List<Finding> findings =
                    CheckTester.of(check).springBootVersion("3.4.0").source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactly(lineOf(bad, "private final RestTemplate"), lineOf(bad, "new RestTemplate()"));
            assertThat(findings).allMatch(finding -> finding.severity() == Severity.INFO);
        }

        @Test
        void appliesFromSpringBoot32() {
            assertThat(CheckTester.of(check).springBootVersion("3.2.0").applies())
                    .isTrue();
            assertThat(CheckTester.of(check).springBootVersion("3.1.9").applies())
                    .isFalse();
        }

        @Test
        void acceptsRestClient() {
            assertThat(CheckTester.of(check)
                            .springBootVersion("4.1.1")
                            .source(fixture(RULE, "good/WeatherClient.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class ValueSprawl {

        private static final String RULE = "spring.value-sprawl";
        private final ValueSprawlCheck check = new ValueSprawlCheck();

        @Test
        void reportsManyValuesUnderOnePrefix() {
            String bad = fixture(RULE, "bad/MailSender.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertSpringBootRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "class MailSender"));
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("MailSender reads 4 settings under app.mail with @Value");
            assertThat(finding.howToFix().explanation()).contains("@ConfigurationProperties(\"app.mail\")");
        }

        @Test
        void theThresholdIsAnOption() {
            assertThat(CheckTester.of(check)
                            .option("min-values", "5")
                            .source(fixture(RULE, "bad/MailSender.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void acceptsConfigurationProperties() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/MailSender.java"))
                            .findings())
                    .isEmpty();
        }
    }
}
