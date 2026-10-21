package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.advisories.Advisory.AffectedPackage;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Match;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Metadata;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdvisoryTest {

    static final AffectedPackage SPRING_WEB = new AffectedPackage(
            "org.springframework:spring-web",
            List.of(
                    range(null, "5.3.40", null),
                    range("6.0.0", "6.0.25", null),
                    range("6.1.0", "6.1.14", null),
                    range("6.1.0", "6.1.12", null)),
            Set.of());

    @ParameterizedTest(name = "{0} affected: {1}")
    @CsvSource({
        "5.3.39, true",
        "5.3.40, false",
        "6.0.0-M1, false",
        "6.0.0, true",
        "6.0.24, true",
        "6.0.25, false",
        "6.1.13, true",
        "6.2.0, false"
    })
    void matchesVersionsInsideItsRanges(String version, boolean affected) {
        assertThat(SPRING_WEB.affects(version)).isEqualTo(affected);
    }

    @Test
    void lastAffectedIsInclusiveAndAnOpenRangeNeverEnds() {
        AffectedRange upTo = range("2.0", null, "2.4");
        AffectedRange open = range("3.0", null, null);

        assertThat(upTo.contains(MavenVersion.parse("2.4"))).isTrue();
        assertThat(upTo.contains(MavenVersion.parse("2.4.1"))).isFalse();
        assertThat(upTo.contains(MavenVersion.parse("1.9"))).isFalse();
        assertThat(open.contains(MavenVersion.parse("42.0"))).isTrue();
        assertThat(open.contains(MavenVersion.parse("2.9"))).isFalse();
    }

    @Test
    void matchesExplicitlyListedVersionsWhenThereAreNoRanges() {
        AffectedPackage listed = new AffectedPackage("g:a", List.of(), Set.of("1.2.3"));

        assertThat(listed.affects("1.2.3")).isTrue();
        assertThat(listed.affects("1.2.4")).isFalse();
        assertThat(listed.fixedVersionFor("1.2.3")).isEmpty();
    }

    @Test
    void suggestsTheLowestFixInTheRangesThatContainTheVersion() {
        assertThat(SPRING_WEB.fixedVersionFor("6.1.5")).contains("6.1.12");
        assertThat(SPRING_WEB.fixedVersionFor("6.1.13")).contains("6.1.14");
        assertThat(SPRING_WEB.fixedVersionFor("5.3.1")).contains("5.3.40");
        assertThat(SPRING_WEB.fixedVersionFor("7.0.0")).isEmpty();
    }

    @Test
    void namesCvesFirstAndFallsBackToItsOwnId() {
        Advisory withCve = advisory("GHSA-1", List.of("GHSA-x", "CVE-2024-38816"));
        Advisory withoutCve = advisory("GHSA-2", List.of("GHSA-y"));

        assertThat(withCve.identifiers()).containsExactly("CVE-2024-38816");
        assertThat(withoutCve.identifiers()).containsExactly("GHSA-2");
    }

    @Test
    void databaseFindsTheAdvisoriesAffectingOneVersionOfAPackage() {
        Advisory advisory =
                new Advisory("GHSA-1", List.of(), "Path traversal", AdvisorySeverity.HIGH, List.of(SPRING_WEB));
        AdvisoryDatabase database =
                new AdvisoryDatabase(List.of(advisory), new Metadata(Instant.EPOCH, Optional.empty()));

        assertThat(database.affecting("org.springframework:spring-web", "6.1.0"))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.advisory()).isEqualTo(advisory);
                    assertThat(match.affected()).isEqualTo(SPRING_WEB);
                });
        assertThat(database.affecting("org.springframework:spring-web", "6.2.0"))
                .isEmpty();
        assertThat(database.affecting("org.example:other", "6.1.0")).isEmpty();
    }

    @Test
    void databaseReportsAnAdvisoryOnceWhenItListsThePackageSeveralTimes() {
        AffectedPackage older = new AffectedPackage("g:a", List.of(range(null, null, "1.9")), Set.of());
        AffectedPackage everything = new AffectedPackage("g:a", List.of(range(null, null, null)), Set.of());
        Advisory advisory =
                new Advisory("GHSA-9", List.of(), "summary", AdvisorySeverity.LOW, List.of(older, everything));
        AdvisoryDatabase database =
                new AdvisoryDatabase(List.of(advisory), new Metadata(Instant.EPOCH, Optional.empty()));

        assertThat(database.affecting("g:a", "1.0"))
                .singleElement()
                .extracting(Match::affected)
                .isEqualTo(older);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"low, LOW", "MODERATE, MODERATE", "Medium, MODERATE", "HIGH, HIGH", "CRITICAL, CRITICAL", "'', UNKNOWN"
    })
    void readsSeveritiesInAnySpelling(String text, AdvisorySeverity severity) {
        assertThat(AdvisorySeverity.of(text)).isEqualTo(severity);
    }

    static Advisory advisory(String id, List<String> aliases) {
        return new Advisory(id, aliases, "summary", AdvisorySeverity.MODERATE, List.of(SPRING_WEB));
    }

    static AffectedRange range(@Nullable String introduced, @Nullable String fixed, @Nullable String lastAffected) {
        return new AffectedRange(
                Optional.ofNullable(introduced), Optional.ofNullable(fixed), Optional.ofNullable(lastAffected));
    }
}
