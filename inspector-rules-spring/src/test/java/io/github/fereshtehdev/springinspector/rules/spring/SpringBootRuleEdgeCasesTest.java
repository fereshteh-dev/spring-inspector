package io.github.fereshtehdev.springinspector.rules.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import org.junit.jupiter.api.Test;

class SpringBootRuleEdgeCasesTest {

    @Test
    void jakartaTransactionalCountsAndClassLevelTransactionsCoverSelfCalls() {
        String jakarta = """
                package com.acme;

                import jakarta.transaction.Transactional;

                class Jobs {
                    @Transactional
                    private void run() {}
                }
                """;
        String classLevel = """
                package com.acme;

                import org.springframework.transaction.annotation.Transactional;

                @Transactional
                class Orders {
                    void place() {
                        save();
                    }

                    @Transactional
                    void save() {}
                }
                """;

        assertThat(CheckTester.of(new TransactionalNotProxiedCheck())
                        .source(jakarta)
                        .findings())
                .hasSize(1);
        assertThat(CheckTester.of(new TransactionalSelfInvocationCheck())
                        .source(classLevel)
                        .findings())
                .isEmpty();
    }

    @Test
    void recursiveCallsAndCallsOnOtherObjectsAreNotSelfInvocation() {
        String source = """
                package com.acme;

                import org.springframework.transaction.annotation.Transactional;

                class Retry {
                    private final Retry other = null;

                    @Transactional
                    void attempt(int left) {
                        if (left > 0) {
                            attempt(left - 1);
                        }
                    }

                    void start() {
                        other.attempt(3);
                    }
                }
                """;

        assertThat(CheckTester.of(new TransactionalSelfInvocationCheck())
                        .source(source)
                        .findings())
                .isEmpty();
    }

    @Test
    void javaxConstraintsGetTheJavaxValidAndValidatedBodiesPass() {
        String request = """
                package com.acme;

                import javax.validation.constraints.NotNull;

                record Order(@NotNull String id) {}
                """;
        String controller = """
                package com.acme;

                import org.springframework.validation.annotation.Validated;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class Orders {
                    @PostMapping("/a")
                    void a(@RequestBody Order order) {}

                    @PostMapping("/b")
                    void b(@Validated @RequestBody Order order) {}

                    @PostMapping("/c")
                    void c(String plain) {}
                }
                """;
        CheckTester tester =
                CheckTester.of(new MissingValidCheck()).source(request).source(controller);

        assertThat(tester.fixed(tester.singleFinding())).contains("import javax.validation.Valid;");
    }

    @Test
    void loopsOverNonEntitiesAndUntypedVariablesAreSkipped() {
        String source = """
                package com.acme;

                class Report {
                    private final NoteRepository noteRepository = null;

                    void print() {
                        for (String note : noteRepository.findAll()) {
                            note.getBytes();
                        }
                        for (var other : noteRepository.findAll()) {
                            other.getBytes();
                        }
                    }
                }
                """;

        assertThat(CheckTester.of(new NPlusOneCheck()).source(source).findings())
                .isEmpty();
    }

    @Test
    void blockingClientsAreFoundThroughThisFieldAccess() {
        String source = """
                package com.acme;

                import org.springframework.jdbc.core.JdbcTemplate;
                import reactor.core.publisher.Flux;

                class Feed {
                    private final JdbcTemplate jdbc = null;

                    Flux<String> items() {
                        this.jdbc.execute("select 1");
                        return Flux.empty();
                    }

                    String plain() {
                        return "x";
                    }
                }
                """;

        assertThat(CheckTester.of(new BlockingInReactiveCheck())
                        .source(source)
                        .singleFinding()
                        .whatsWrong()
                        .description())
                .isEqualTo("this.jdbc.execute() blocks inside reactive method items");
    }

    @Test
    void valuesWithoutAPrefixOrInOtherClassesDoNotCount() {
        String source = """
                package com.acme;

                import org.springframework.beans.factory.annotation.Value;

                class Settings {
                    @Value("${a.b.c}") String one;
                    @Value("${a.b.d}") String two;
                    @Value("plain") String three;
                    @Value(value = "${a.b.e}") String four;

                    static class Inner {
                        @Value("${a.b.f}") String five;
                    }
                }
                """;

        assertThat(CheckTester.of(new ValueSprawlCheck())
                        .option("min-values", "3")
                        .source(source)
                        .findings())
                .isEmpty();
    }
}
