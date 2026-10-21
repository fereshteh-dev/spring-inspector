package io.github.fereshtehdev.springinspector.rules.cleancode;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleanCodeRuleEdgeCasesTest {

    @Test
    void everyKindOfBlockStatementCountsAsALevel() {
        String source = """
                package com.acme;

                class Loops {
                    void run(int n, Object lock) throws Exception {
                        while (n > 0) {
                            do {
                                switch (n) {
                                    default -> {
                                        try {
                                            synchronized (lock) {
                                                n--;
                                            }
                                        } finally {
                                            n--;
                                        }
                                    }
                                }
                            } while (n > 5);
                        }
                    }
                }
                """;

        Finding finding = CheckTester.of(new DeepNestingCheck()).source(source).singleFinding();

        assertThat(finding.whatsWrong().description()).isEqualTo("run nests 5 levels deep; the limit is 4");
    }

    @Test
    void throwableAndQualifiedExceptionAreGenericToo() {
        String source = """
                package com.acme;

                class Jobs {
                    void a() {
                        try { work(); } catch (Throwable t) { log(t); }
                        try { work(); } catch (java.lang.Exception e) { log(e); }
                        try { work(); } catch (IllegalStateException e) { log(e); }
                    }

                    void work() {}

                    void log(Throwable t) {}
                }
                """;

        assertThat(CheckTester.of(new GenericCatchCheck()).source(source).findings())
                .hasSize(2);
    }

    @Test
    void setsAndMapsReturnTheirEmptyValues() {
        CheckTester tester = CheckTester.of(new ReturnNullCheck()).source("""
                package com.acme;

                import java.util.Map;
                import java.util.Set;

                class Lookup {
                    Set<String> names() {
                        return null;
                    }

                    Map<String, String> pairs() {
                        return null;
                    }

                    int[] numbers() {
                        return null;
                    }
                }
                """);
        List<Finding> findings = tester.findings();

        assertThat(findings).hasSize(2);
        assertThat(tester.fixed(findings.getFirst())).contains("return Set.of();");
        assertThat(tester.fixed(findings.getLast())).contains("return Map.of();");
    }

    @Test
    void unusedMembersInMultiVariableDeclarationsAndPublicMembersAreLeftAlone() {
        String source = """
                package com.acme;

                class Counters {
                    private int a, b;
                    public int c;

                    public void unusedButPublic() {}

                    private void readObject(java.io.ObjectInputStream in) {}
                }
                """;

        assertThat(CheckTester.of(new UnusedPrivateCheck()).source(source).findings())
                .isEmpty();
    }

    @Test
    void publicOptionalFieldsAreReported() {
        assertThat(CheckTester.of(new OptionalMisuseCheck())
                        .source("""
                                package com.acme;

                                import java.util.Optional;

                                public class Settings {
                                    public Optional<String> name;
                                    public String plain;
                                    protected void set(String value) {}
                                }
                                """)
                        .singleFinding()
                        .whatsWrong()
                        .description())
                .isEqualTo("Field name is an Optional");
    }

    @Test
    void classesWithBehaviourOrNestedTypesStayClasses() {
        String source = """
                package com.acme;

                class Timer {
                    private static final int MAX = 3;
                    private final long start;

                    Timer(long start) {
                        this.start = start;
                    }

                    long elapsed(long now) {
                        return now - start;
                    }
                }

                class Wrapper {
                    private final String value;

                    Wrapper(String value) {
                        this.value = value;
                    }

                    String value() {
                        String copy = value;
                        return copy;
                    }

                    static class Nested {}
                }

                class Empty {
                    private static final int ONLY_STATIC = 1;
                }
                """;

        assertThat(CheckTester.of(new DataClassToRecordCheck())
                        .javaVersion("21")
                        .source(source)
                        .findings())
                .isEmpty();
    }

    @Test
    void chainsBrokenByOtherConditionsOrValuesAreShort() {
        String source = """
                package com.acme;

                class Kinds {
                    String kind(Object a, Object b) {
                        if (a instanceof String) {
                            return "s";
                        } else if (b instanceof Integer) {
                            return "i";
                        } else if (a instanceof Long) {
                            return "l";
                        } else if (a == null) {
                            return "n";
                        }
                        if (a != null) {
                            return "x";
                        }
                        return "?";
                    }
                }
                """;

        assertThat(CheckTester.of(new InstanceofChainCheck())
                        .javaVersion("21")
                        .source(source)
                        .findings())
                .isEmpty();
    }
}
