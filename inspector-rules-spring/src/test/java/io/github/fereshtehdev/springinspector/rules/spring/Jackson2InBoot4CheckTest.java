package io.github.fereshtehdev.springinspector.rules.spring;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class Jackson2InBoot4CheckTest {

    private static final String FIXTURES = "fixtures/spring.jackson2-in-boot4/";

    private final Jackson2InBoot4Check check = new Jackson2InBoot4Check();

    private CheckTester boot4(String source) {
        return CheckTester.of(check).springBootVersion("4.1.1").source(source);
    }

    @Test
    void identifiesItselfAsASpringBootRule() {
        assertThat(check.id()).isEqualTo(Jackson2InBoot4Check.ID);
        assertThat(check.category()).isEqualTo(Category.SPRING_BOOT);
    }

    @Test
    void appliesOnlyToSpringBoot4AndLater() {
        assertThat(CheckTester.of(check).springBootVersion("4.0.0").applies()).isTrue();
        assertThat(CheckTester.of(check).springBootVersion("3.5.7").applies()).isFalse();
        assertThat(CheckTester.of(check).applies()).isFalse();
    }

    @Test
    void reportsEachJackson2ImportButNotTheSharedAnnotations() {
        List<Finding> findings =
                boot4(resource(FIXTURES + "bad/OrderJson.java")).findings();

        assertThat(findings)
                .extracting(finding -> finding.location().lines().start())
                .containsExactly(4, 5);
        assertThat(findings.getLast().whatsWrong().description())
                .isEqualTo("Jackson 2 type com.fasterxml.jackson.databind.ObjectMapper is used in a Spring Boot 4"
                        + " project");
    }

    @Test
    void movesImportsOfTypesThatKeptTheirNameToJackson3() {
        CheckTester bad = boot4(resource(FIXTURES + "bad/OrderJson.java"));
        List<Finding> findings = bad.findings();

        CheckTester afterFirst = bad.afterFix(findings.getFirst());
        String fixed = afterFirst.fixed(afterFirst.singleFinding());

        assertThat(fixed).isEqualTo(resource(FIXTURES + "good/OrderJson.java"));
    }

    @Test
    void offersNoPatchWhenTheCodeReliesOnWhatJackson3Changed() {
        List<Finding> catching = boot4("""
                        package com.acme;

                        import com.fasterxml.jackson.core.JsonProcessingException;
                        import com.fasterxml.jackson.databind.ObjectMapper;

                        class Reader {
                            Object read(ObjectMapper mapper, String json) {
                                try {
                                    return mapper.readValue(json, Object.class);
                                } catch (JsonProcessingException e) {
                                    return null;
                                }
                            }
                        }
                        """).findings();
        List<Finding> mutating = boot4("""
                        package com.acme;

                        import com.fasterxml.jackson.databind.ObjectMapper;
                        import com.fasterxml.jackson.databind.SerializationFeature;

                        class Config {
                            ObjectMapper mapper() {
                                return new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
                            }
                        }
                        """).findings();

        assertThat(catching)
                .hasSize(2)
                .allMatch(finding -> finding.howToFix().patch().isEmpty());
        assertThat(mutating)
                .hasSize(2)
                .allMatch(finding -> finding.howToFix().patch().isEmpty());
    }

    @Test
    void offersNoPatchForWildcardsOrQualifiedUses() {
        assertThat(boot4("""
                        package com.acme;

                        import com.fasterxml.jackson.databind.*;

                        class Wildcard {
                            com.fasterxml.jackson.databind.ObjectMapper mapper;
                        }
                        """).singleFinding().howToFix().patch()).isEmpty();
    }

    @Test
    void acceptsJackson3() {
        assertThat(boot4(resource(FIXTURES + "good/OrderJson.java")).findings()).isEmpty();
    }
}
