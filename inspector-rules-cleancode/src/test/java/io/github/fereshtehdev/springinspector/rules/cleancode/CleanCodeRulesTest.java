package io.github.fereshtehdev.springinspector.rules.cleancode;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.lineOf;
import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CleanCodeRulesTest {

    private static String fixture(String rule, String path) {
        return resource("fixtures/" + rule + "/" + path);
    }

    private static List<Integer> lines(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.location().lines().start())
                .toList();
    }

    private static void assertCleanCodeRule(Check check, String id) {
        assertThat(check.id().value()).isEqualTo(id);
        assertThat(check.category()).isEqualTo(Category.CLEAN_CODE);
    }

    @Nested
    class DeepNesting {

        private static final String RULE = "cleancode.deep-nesting";
        private final DeepNestingCheck check = new DeepNestingCheck();

        @Test
        void reportsTheFirstStatementPastTheLimitOncePerMethod() {
            String bad = fixture(RULE, "bad/Shipping.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertCleanCodeRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "if (item.fragile())"));
            assertThat(finding.whatsWrong().description()).isEqualTo("cost nests 5 levels deep; the limit is 4");
        }

        @Test
        void elseIfChainsAndGuardClausesStayFlat() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Shipping.java"))
                            .findings())
                    .isEmpty();
        }

        @Test
        void theLimitIsAnOption() {
            assertThat(CheckTester.of(check)
                            .option("max-depth", "5")
                            .source(fixture(RULE, "bad/Shipping.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class LongParameterList {

        private static final String RULE = "cleancode.long-parameter-list";
        private final LongParameterListCheck check = new LongParameterListCheck();

        @Test
        void reportsMethodsWithTooManyParameters() {
            Finding finding = CheckTester.of(check)
                    .source(fixture(RULE, "bad/Invoices.java"))
                    .singleFinding();

            assertCleanCodeRule(check, RULE);
            assertThat(finding.whatsWrong().description()).isEqualTo("create takes 8 parameters; the limit is 7");
        }

        @Test
        void leavesConstructorsOverridesAndShortListsAlone() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Invoices.java"))
                            .findings())
                    .isEmpty();
            assertThat(CheckTester.of(check).source("""
                                    package com.acme;

                                    class Handler implements Api {
                                        @Override
                                        public void handle(int a, int b, int c, int d, int e, int f, int g, int h) {}
                                    }
                                    """).findings()).isEmpty();
        }
    }

    @Nested
    class LargeClass {

        private final LargeClassCheck check = new LargeClassCheck();

        @Test
        void reportsClassesOverTheLineLimit() {
            String source = "package com.acme;\n\nclass Big {\n" + "    int a = 1;\n".repeat(4) + "}\n";

            Finding finding = CheckTester.of(check)
                    .option("max-lines", "3")
                    .source(source)
                    .singleFinding();

            assertCleanCodeRule(check, "cleancode.large-class");
            assertThat(finding.whatsWrong().description()).isEqualTo("Big has 4 lines of code; the limit is 3");
            assertThat(finding.whatsWrong().snippet()).isEqualTo("class Big {");
            assertThat(CheckTester.of(check).source(source).findings()).isEmpty();
        }
    }

    @Nested
    class GenericCatch {

        private static final String RULE = "cleancode.generic-catch";
        private final GenericCatchCheck check = new GenericCatchCheck();

        @Test
        void reportsCatchAllHandlersThatCarryOn() {
            String bad = fixture(RULE, "bad/Importer.java");

            Finding finding = CheckTester.of(check).source(bad).singleFinding();

            assertCleanCodeRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "catch (Exception e)"));
        }

        @Test
        void acceptsSpecificCatchesAndRethrowingCleanup() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Importer.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class ReturnNull {

        private static final String RULE = "cleancode.return-null";
        private final ReturnNullCheck check = new ReturnNullCheck();

        @Test
        void reportsNullForOptionalsAndCollectionsAndReturnsTheEmptyValue() {
            String bad = fixture(RULE, "bad/Customers.java");
            CheckTester tester = CheckTester.of(check).source(bad);

            List<Finding> findings = tester.findings();

            assertCleanCodeRule(check, RULE);
            assertThat(findings).extracting(Finding::severity).containsExactly(Severity.MAJOR, Severity.MINOR);
            assertThat(tester.fixed(findings.getFirst())).contains("            return Optional.empty();\n        }");
            assertThat(tester.fixed(findings.getLast())).contains("return List.of();");
        }

        @Test
        void addsTheImportTheEmptyValueNeeds() {
            CheckTester tester = CheckTester.of(check).source("""
                    package com.acme;

                    import java.util.Collection;

                    class Tags {
                        Collection<String> all() {
                            return null;
                        }
                    }
                    """);

            assertThat(tester.fixed(tester.singleFinding()))
                    .contains("import java.util.List;\nimport java.util.Collection;")
                    .contains("return List.of();");
        }

        @Test
        void ignoresLambdasAndOtherReturnTypes() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Customers.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class OptionalMisuse {

        private static final String RULE = "cleancode.optional-misuse";
        private final OptionalMisuseCheck check = new OptionalMisuseCheck();

        @Test
        void reportsOptionalFieldsAndParameters() {
            String bad = fixture(RULE, "bad/Profile.java");

            List<Finding> findings = CheckTester.of(check).source(bad).findings();

            assertCleanCodeRule(check, RULE);
            assertThat(lines(findings))
                    .containsExactly(
                            lineOf(bad, "protected Optional<String> nickname"),
                            lineOf(bad, "Optional<String> newName"));
        }

        @Test
        void acceptsOptionalReturnValuesAndPrivateHelpers() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Profile.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class UnusedPrivate {

        private static final String RULE = "cleancode.unused-private";
        private final UnusedPrivateCheck check = new UnusedPrivateCheck();

        @Test
        void reportsAndDeletesUnusedPrivateMembersWithTheirComments() {
            String bad = fixture(RULE, "bad/Pricing.java");
            CheckTester tester = CheckTester.of(check).source(bad);

            List<Finding> findings = tester.findings();

            assertCleanCodeRule(check, RULE);
            assertThat(findings)
                    .extracting(finding -> finding.whatsWrong().description())
                    .containsExactly(
                            "Private field legacyDiscount is never used", "Private method roundLegacy is never used");
            assertThat(tester.fixed(findings.getLast()))
                    .doesNotContain("roundLegacy")
                    .doesNotContain("Rounds the old way")
                    .contains("return net * (1 + vat);");
        }

        @Test
        void leavesUsedAnnotatedAndSerializationMembersAlone() {
            assertThat(CheckTester.of(check)
                            .source(fixture(RULE, "good/Pricing.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class DataClassToRecord {

        private static final String RULE = "cleancode.data-class-to-record";
        private final DataClassToRecordCheck check = new DataClassToRecordCheck();

        @Test
        void suggestsARecordForPlainDataClasses() {
            Finding finding = CheckTester.of(check)
                    .javaVersion("21")
                    .source(fixture(RULE, "bad/Money.java"))
                    .singleFinding();

            assertCleanCodeRule(check, RULE);
            assertThat(finding.severity()).isEqualTo(Severity.INFO);
            assertThat(finding.howToFix().explanation()).contains("record Money(BigDecimal amount, String currency)");
        }

        @Test
        void appliesFromJava16AndSkipsEntitiesAndMutableClasses() {
            assertThat(CheckTester.of(check).javaVersion("16").applies()).isTrue();
            assertThat(CheckTester.of(check).javaVersion("11").applies()).isFalse();
            assertThat(CheckTester.of(check)
                            .javaVersion("21")
                            .source(fixture(RULE, "good/Money.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class InstanceofChain {

        private static final String RULE = "cleancode.instanceof-chain";
        private final InstanceofChainCheck check = new InstanceofChainCheck();

        @Test
        void suggestsAPatternSwitchForLongTypeTestChains() {
            String bad = fixture(RULE, "bad/Shapes.java");

            Finding finding =
                    CheckTester.of(check).javaVersion("21").source(bad).singleFinding();

            assertCleanCodeRule(check, RULE);
            assertThat(finding.location().lines().start()).isEqualTo(lineOf(bad, "if (shape instanceof Circle"));
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("An if/else chain tests the type of shape 3 times");
        }

        @Test
        void appliesFromJava21AndAcceptsShortChainsAndSwitches() {
            assertThat(CheckTester.of(check).javaVersion("17").applies()).isFalse();
            assertThat(CheckTester.of(check)
                            .javaVersion("21")
                            .source(fixture(RULE, "good/Shapes.java"))
                            .findings())
                    .isEmpty();
        }
    }

    @Nested
    class ThreadPoolForIo {

        private static final String RULE = "cleancode.thread-pool-for-io";
        private final ThreadPoolForIoCheck check = new ThreadPoolForIoCheck();

        @Test
        void hintsAtVirtualThreadsForPlatformPools() {
            Finding finding = CheckTester.of(check)
                    .javaVersion("25")
                    .source(fixture(RULE, "bad/Notifier.java"))
                    .singleFinding();

            assertCleanCodeRule(check, RULE);
            assertThat(finding.severity()).isEqualTo(Severity.INFO);
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("Executors.newFixedThreadPool creates platform threads where virtual threads may fit");
        }

        @Test
        void appliesFromJava21AndAcceptsVirtualThreads() {
            assertThat(CheckTester.of(check).javaVersion("17").applies()).isFalse();
            assertThat(CheckTester.of(check)
                            .javaVersion("25")
                            .source(fixture(RULE, "good/Notifier.java"))
                            .findings())
                    .isEmpty();
        }
    }
}
