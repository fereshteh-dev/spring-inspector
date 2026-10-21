package io.github.fereshtehdev.springinspector.rules.cleancode;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwallowedExceptionCheckTest {

    private static final String FIXTURES = "fixtures/cleancode.swallowed-exception/";

    private final SwallowedExceptionCheck check = new SwallowedExceptionCheck();

    private CheckTester bad() {
        return CheckTester.of(check).source(resource(FIXTURES + "bad/Poller.java"));
    }

    @Test
    void identifiesItselfAsACleanCodeRule() {
        assertThat(check.id()).isEqualTo(SwallowedExceptionCheck.ID);
        assertThat(check.category()).isEqualTo(Category.CLEAN_CODE);
    }

    @Test
    void reportsEmptyCatchBlocksAndRanksALostInterruptHigher() {
        List<Finding> findings = bad().findings();

        assertThat(findings)
                .extracting(finding -> finding.location().lines().start(), Finding::severity)
                .containsExactly(tuple(13, Severity.MINOR), tuple(17, Severity.MAJOR));
        assertThat(findings.getFirst().howToFix().patch()).isEmpty();
    }

    @Test
    void restoresTheInterruptFlag() {
        CheckTester bad = bad();

        String fixed = bad.fixed(bad.findings().getLast());

        assertThat(fixed).contains("""
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                """);
    }

    @Test
    void acceptsHandledCommentedOrDeliberatelyIgnoredExceptions() {
        assertThat(CheckTester.of(check)
                        .source(resource(FIXTURES + "good/Poller.java"))
                        .findings())
                .isEmpty();
    }

    @Test
    void anEmptyMultiCatchWithInterruptedExceptionGetsNoAutomaticFix() {
        Finding finding = CheckTester.of(check).source("""
                        package com.acme;

                        import java.util.concurrent.ExecutionException;
                        import java.util.concurrent.Future;

                        class Waiter {
                            void await(Future<?> future) {
                                try {
                                    future.get();
                                } catch (InterruptedException | ExecutionException e) {
                                }
                            }
                        }
                        """).singleFinding();

        assertThat(finding.severity()).isEqualTo(Severity.MINOR);
        assertThat(finding.howToFix().patch()).isEmpty();
    }

    @Test
    void indentsTheFixWithTabsInTabIndentedFiles() {
        CheckTester tabs = CheckTester.of(check).source("""
                package com.acme;

                class Sleeper {
                \tvoid nap() {
                \t\ttry {
                \t\t\tThread.sleep(1);
                \t\t} catch (InterruptedException e) {}
                \t}
                }
                """);

        assertThat(tabs.fixed(tabs.singleFinding()))
                .contains("\t\t} catch (InterruptedException e) {\n\t\t\tThread.currentThread().interrupt();\n\t\t}");
    }
}
