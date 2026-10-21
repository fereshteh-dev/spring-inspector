package io.github.fereshtehdev.springinspector.rules.security;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlStringConcatenationCheckTest {

    private static final String FIXTURES = "fixtures/security.sql-concatenation/";

    private final SqlStringConcatenationCheck check = new SqlStringConcatenationCheck();

    @Test
    void identifiesItselfAsASecurityRule() {
        assertThat(check.id()).isEqualTo(SqlStringConcatenationCheck.ID);
        assertThat(check.category()).isEqualTo(Category.SECURITY);
    }

    @Test
    void reportsQueriesBuiltFromValuesWhetherInlineFormattedOrThroughALocal() {
        List<Finding> findings = CheckTester.of(check)
                .source(resource(FIXTURES + "bad/OrderQueries.java"))
                .findings();

        assertThat(findings)
                .extracting(finding -> finding.location().lines().start(), Finding::severity, Finding::confidence)
                .containsExactly(
                        tuple(24, Severity.MAJOR, Confidence.MEDIUM),
                        tuple(29, Severity.MAJOR, Confidence.MEDIUM),
                        tuple(34, Severity.BLOCKER, Confidence.HIGH));
        assertThat(findings.getFirst().whatsWrong().description()).contains("queryForList()");
        assertThat(findings.getFirst().howToFix().patch()).isEmpty();
    }

    @Test
    void acceptsBindParametersConstantsAndNonDatabaseApis() {
        List<Finding> findings = CheckTester.of(check)
                .source(resource(FIXTURES + "good/SafeOrderQueries.java"))
                .source(resource(FIXTURES + "good/AuditLog.java"))
                .findings();

        assertThat(findings).isEmpty();
    }

    @Test
    void ignoresBuiltStringsThatDoNotLookLikeSql() {
        List<Finding> findings = CheckTester.of(check).source("""
                        package com.acme;

                        class Cache {
                            void evict(Store store, String key) {
                                store.update("cache:" + key);
                            }
                        }
                        """).findings();

        assertThat(findings).isEmpty();
    }
}
