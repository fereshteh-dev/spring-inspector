package io.github.fereshtehdev.springinspector.rules.security;

import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.fixture;
import static io.github.fereshtehdev.springinspector.rules.security.Fixtures.lines;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InputHandlingRulesTest {

    @Nested
    class UnsafeDeserialization {

        private static final String RULE = "security.unsafe-deserialization";
        private final UnsafeDeserializationCheck check = new UnsafeDeserializationCheck();

        @Test
        void reportsUnfilteredStreamsDefaultTypingAndClassTypeIds() {
            String bad = fixture(RULE, "bad/SessionStore.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(UnsafeDeserializationCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactly(
                            lineOf(bad, "new ObjectInputStream"),
                            lineOf(bad, "activateDefaultTyping"),
                            lineOf(bad, "@JsonTypeInfo(use"));
        }

        @Test
        void acceptsFilteredStreamsAndNamedSubtypes() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/SessionStore.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void reportsXStreamWithoutAllowList() {
            assertThat(CheckTester.of(check)
                            .source("""
                                    package com.acme;

                                    import com.thoughtworks.xstream.XStream;

                                    class Reader {
                                        Object read(String xml) {
                                            return new XStream().fromXML(xml);
                                        }
                                    }
                                    """)
                            .singleFinding()
                            .whatsWrong()
                            .description())
                    .isEqualTo("XStream is used without an allow-list of types");
        }
    }

    @Nested
    class Xxe {

        private static final String RULE = "security.xxe";
        private final XxeCheck check = new XxeCheck();

        @Test
        void reportsUnhardenedParserFactories() {
            String bad = fixture(RULE, "bad/InvoiceImporter.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(XxeCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactly(lineOf(bad, "DocumentBuilderFactory.newInstance()"), lineOf(bad, "newFactory()"));
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("DocumentBuilderFactory is created without disabling external entities");
        }

        @Test
        void acceptsAHardenedParser() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/InvoiceImporter.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class PathTraversal {

        private static final String RULE = "security.path-traversal";
        private final PathTraversalCheck check = new PathTraversalCheck();

        @Test
        void reportsPathsBuiltFromRequestValues() {
            String bad = fixture(RULE, "bad/DownloadController.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(PathTraversalCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(lineOf(bad, "base.resolve(name)"), lineOf(bad, "new File(location)"));
        }

        @Test
        void acceptsValidatedPathsAndInternalCallers() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/DownloadController.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class Ssrf {

        private static final String RULE = "security.ssrf";
        private final SsrfCheck check = new SsrfCheck();

        @Test
        void reportsRequestsToUrlsTheCallerChooses() {
            String bad = fixture(RULE, "bad/PreviewController.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(SsrfCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings))
                    .containsExactlyInAnyOrder(
                            lineOf(bad, "getForObject(url"),
                            lineOf(bad, "\"https://\" + host"),
                            lineOf(bad, "URI.create(target)"));
        }

        @Test
        void acceptsAFixedHostWithRequestValuesInThePath() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/PreviewController.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void reportsUrlObjectsBuiltFromServletParameters() {
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    import jakarta.servlet.http.HttpServletRequest;
                                    import java.net.URL;

                                    class Fetcher {
                                        Object fetch(HttpServletRequest request) throws Exception {
                                            return new URL(request.getParameter("target")).openStream();
                                        }
                                    }
                                    """).findings()).hasSize(1);
        }
    }

    @Nested
    class InsecureRandom {

        private static final String RULE = "security.insecure-random";
        private final InsecureRandomCheck check = new InsecureRandomCheck();

        @Test
        void reportsPredictableGeneratorsForSecurityValues() {
            String bad = fixture(RULE, "bad/ResetTokens.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertThat(check.id()).isEqualTo(InsecureRandomCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "new Random()"), lineOf(bad, "Math.random()"));
            assertThat(findings.getLast().howToFix().patch()).isEmpty();
        }

        @Test
        void switchesToSecureRandomAndImportsIt() {
            CheckTester tester = CheckTester.of(check).source(fixture(RULE, "bad/ResetTokens.java"));

            String fixed = tester.fixed(tester.findings().getFirst());

            assertThat(fixed)
                    .contains("import java.security.SecureRandom;\nimport java.util.Random;")
                    .contains("private final Random tokenRandom = new SecureRandom();");
        }

        @Test
        void addsTheImportAfterThePackageWhenThereAreNoImports() {
            CheckTester tester = CheckTester.of(check).source("""
                    package com.acme;

                    class Otp {
                        int otp = new java.util.Random().nextInt();
                    }
                    """);

            assertThat(tester.fixed(tester.singleFinding()))
                    .startsWith("package com.acme;\n\nimport java.security.SecureRandom;\n");
        }

        @Test
        void acceptsSecureRandomAndRandomForHarmlessUses() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/ResetTokens.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class MassAssignment {

        private static final String RULE = "security.mass-assignment";
        private final MassAssignmentCheck check = new MassAssignmentCheck();

        @Test
        void reportsRequestBodiesBoundToEntities() {
            String bad = fixture(RULE, "bad/AccountController.java");

            List<Finding> findings = CheckTester.of(check)
                    .source(bad)
                    .source(fixture(RULE, "bad/Account.java"))
                    .findings();

            assertThat(check.id()).isEqualTo(MassAssignmentCheck.ID);
            assertThat(check.category()).isEqualTo(Category.SECURITY);
            assertThat(lines(findings)).containsExactly(lineOf(bad, "@RequestBody Account"));
            assertThat(findings.getFirst().whatsWrong().description())
                    .isEqualTo("The request is bound directly to the entity Account");
        }

        @Test
        void acceptsRequestRecords() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/AccountController.java"))
                            .source(fixture(RULE, "bad/Account.java"))
                            .findings())
                    .isEmpty();
        }
    }
}
