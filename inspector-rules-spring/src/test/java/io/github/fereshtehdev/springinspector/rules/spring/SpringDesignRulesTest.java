package io.github.fereshtehdev.springinspector.rules.spring;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpringDesignRulesTest {

    private static String fixture(String rule, String path) {
        return resource("fixtures/" + rule + "/" + path);
    }

    private static List<Integer> lines(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.location().lines().start())
                .toList();
    }

    private static List<String> descriptions(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.whatsWrong().description())
                .toList();
    }

    private static void assertSpringBootRule(Check check, String id) {
        assertThat(check.id().value()).isEqualTo(id);
        assertThat(check.category()).isEqualTo(Category.SPRING_BOOT);
    }

    private static void assertNoFindings(Check check, String rule, String file) {
        assertThat(CheckTester.of(check).source(fixture(rule, "good/" + file)).findings())
                .isEmpty();
    }

    @Nested
    class ProxySelfInvocation {

        private static final String RULE = "spring.proxy-self-invocation";
        private final ProxySelfInvocationCheck check = new ProxySelfInvocationCheck();

        @Test
        void reportsAsyncAndCachedMethodsCalledOnTheSameObject() {
            String bad = fixture(RULE, "bad/ReportService.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "this.sendByMail"), lineOf(bad, "return find("));
            assertThat(descriptions(findings))
                    .containsExactly(
                            "publish() calls @Async sendByMail() on the same object, so it runs on the caller's thread",
                            "latest() calls @Cacheable find() on the same object, so the cache is never used");
        }

        @Test
        void acceptsCallsThroughOtherBeansAndRecursion() {
            assertNoFindings(check, RULE, "ReportService.java");
        }

        @Test
        void recognisesTheRetryableOfSpringFramework7() {
            String source = """
                    package com.acme;

                    import org.springframework.resilience.annotation.Retryable;

                    class Prices {
                        double price() {
                            return fetch();
                        }

                        @Retryable
                        double fetch() {
                            return 1;
                        }
                    }
                    """;

            assertThat(CheckTester.of(check)
                            .source(source)
                            .singleFinding()
                            .whatsWrong()
                            .description())
                    .isEqualTo("price() calls @Retryable fetch() on the same object, so a failure is not retried");
        }
    }

    @Nested
    class RemoteCallInTransaction {

        private static final String RULE = "spring.remote-call-in-transaction";
        private final RemoteCallInTransactionCheck check = new RemoteCallInTransactionCheck();

        @Test
        void reportsTheFirstHttpOrModelCallOfEachTransactionalMethod() {
            String bad = fixture(RULE, "bad/PaymentService.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "bank.post()"), lineOf(bad, "chat.prompt()"));
            assertThat(descriptions(findings).getFirst())
                    .isEqualTo("pay() makes a remote call through bank inside a transaction");
        }

        @Test
        void acceptsRemoteCallsMadeBeforeTheTransaction() {
            assertNoFindings(check, RULE, "PaymentService.java");
        }

        @Test
        void treatsPublicMethodsOfATransactionalClassAsTransactional() {
            String source = """
                    package com.acme;

                    import java.net.http.HttpClient;
                    import org.springframework.transaction.annotation.Transactional;

                    @Transactional
                    class Sync {
                        private final HttpClient http;

                        Sync(HttpClient http) {
                            this.http = http;
                        }

                        public void run(java.net.http.HttpRequest request) throws Exception {
                            this.http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
                        }

                        void helper(java.net.http.HttpRequest request) throws Exception {
                            http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
                        }
                    }
                    """;

            assertThat(CheckTester.of(check)
                            .source(source)
                            .singleFinding()
                            .whatsWrong()
                            .description())
                    .isEqualTo("run() makes a remote call through this.http inside a transaction");
        }
    }

    @Nested
    class HttpClientTimeout {

        private static final String RULE = "spring.http-client-without-timeout";
        private final HttpClientTimeoutCheck check = new HttpClientTimeoutCheck();

        @Test
        void reportsJdkAndSpringClientsWithoutTimeouts() {
            String bad = fixture(RULE, "bad/Clients.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "HttpClient.newHttpClient()"),
                            lineOf(bad, "HttpClient.newBuilder()"),
                            lineOf(bad, "RestClient.create("),
                            lineOf(bad, "RestClient.builder()"),
                            lineOf(bad, "new RestTemplate()"));
        }

        @Test
        void buildsTheJdkClientWithAConnectTimeout() {
            CheckTester tester = CheckTester.of(check).source("""
                    package com.acme;

                    import java.net.http.HttpClient;

                    class Client {
                        HttpClient http = HttpClient.newHttpClient();
                    }
                    """);

            assertThat(tester.fixed(tester.singleFinding()))
                    .contains("import java.time.Duration;")
                    .contains("HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();");
        }

        @Test
        void acceptsClientsWithTimeoutsOrFromSpringBootsBuilder() {
            assertNoFindings(check, RULE, "Clients.java");
        }

        @Test
        void acceptsARestTemplateThatGetsARequestFactoryLater() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import org.springframework.web.client.RestTemplate;

                                    class Client {
                                        RestTemplate template(Object factory) {
                                            RestTemplate template = new RestTemplate();
                                            template.setRequestFactory(null);
                                            return template;
                                        }
                                    }
                                    """).findings()).isEmpty();
        }
    }

    @Nested
    class EagerFetch {

        private static final String RULE = "spring.eager-fetch";
        private final EagerFetchCheck check = new EagerFetchCheck();

        @Test
        void reportsEagerCollections() {
            String bad = fixture(RULE, "bad/Owner.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertSpringBootRule(check, RULE);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "@OneToMany"), lineOf(bad, "@ElementCollection"));
            assertThat(descriptions(findings).getFirst()).isEqualTo("@OneToMany collection is loaded eagerly");
        }

        @Test
        void acceptsLazyCollectionsAndEagerSingleAssociations() {
            assertNoFindings(check, RULE, "Owner.java");
        }
    }

    @Nested
    class LombokDataEntity {

        private static final String RULE = "spring.lombok-data-entity";
        private final LombokDataEntityCheck check = new LombokDataEntityCheck();

        @Test
        void reportsDataOnAnEntity() {
            String bad = fixture(RULE, "bad/Customer.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertSpringBootRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "@Data"));
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("Entity Customer uses @Data, which bases equals and hashCode on every field");
        }

        @Test
        void acceptsEqualityOnExplicitlyIncludedFields() {
            assertNoFindings(check, RULE, "Customer.java");
        }

        @Test
        void leavesDataClassesThatAreNotEntitiesAlone() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import lombok.Data;

                                    @Data
                                    class OrderForm {
                                        private String name;
                                    }
                                    """).findings()).isEmpty();
        }
    }
}
