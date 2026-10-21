package io.github.fereshtehdev.springinspector.rules.cleancode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import org.junit.jupiter.api.Test;

class LongMethodCheckTest {

    private static final String SOURCE = """
            package com.acme;

            class Report {

                Report() {
                    int a = 1;
                    int b = 2;
                    int c = 3;
                    int d = 4;
                }

                int total(int a,
                          int b) {
                    // Comments and blank lines are not code.

                    /* Neither is
                       a block comment. */
                    int sum = a;
                    sum += b;
                    return sum;
                }

                abstract static class Shape {
                    abstract double area();
                }
            }
            """;

    @Test
    void identifiesItselfAsACleanCodeRuleWithADefaultLimit() {
        LongMethodCheck check = new LongMethodCheck();

        assertThat(check.id()).isEqualTo(LongMethodCheck.ID);
        assertThat(check.category()).isEqualTo(Category.CLEAN_CODE);
        assertThat(CheckTester.of(check).source(SOURCE).findings()).isEmpty();
    }

    @Test
    void reportsMethodsAndConstructorsWithMoreCodeLinesThanTheLimit() {
        Finding finding = CheckTester.of(new LongMethodCheck())
                .option("max-lines", "3")
                .source(SOURCE)
                .singleFinding();

        assertThat(finding.location().lines()).isEqualTo(LineRange.of(5, 10));
        assertThat(finding.severity()).isEqualTo(Severity.MINOR);
        assertThat(finding.whatsWrong().description()).isEqualTo("Report has 4 lines of code; the limit is 3");
        assertThat(finding.whatsWrong().snippet()).isEqualTo("    Report() {");
    }

    @Test
    void countsOnlyCodeLines() {
        Finding finding = CheckTester.of(new LongMethodCheck())
                .option("max-lines", "2")
                .source(SOURCE)
                .findings()
                .getLast();

        assertThat(finding.whatsWrong().description()).isEqualTo("total has 3 lines of code; the limit is 2");
        assertThat(finding.whatsWrong().snippet()).isEqualTo("    int total(int a,");
    }

    @Test
    void anInvalidLimitFailsTheCheckWithAClearMessage() {
        CheckTester tester = CheckTester.of(new LongMethodCheck())
                .option("max-lines", "many")
                .source(SOURCE);

        assertThatThrownBy(tester::findings)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'max-lines' must be a positive whole number");
    }
}
