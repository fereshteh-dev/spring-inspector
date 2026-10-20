package io.github.fereshtehdev.springinspector.core.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionTest {

    @ParameterizedTest
    @CsvSource({
        "25, 25.0.0",
        "4.1, 4.1.0",
        "4.1.1, 4.1.1",
        "2.0.0-M4, 2.0.0",
        "21.0.1+12, 21.0.1",
        "1.8.0_362, 1.8.0",
        "2.7.18.RELEASE, 2.7.18",
        "' 3.5.6 ', 3.5.6"
    })
    void parsesNumericPartAndIgnoresQualifiers(String text, String expected) {
        assertThat(Version.parse(text)).hasToString(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "v4.1", "4..1"})
    void rejectsTextThatIsNotAVersion(String text) {
        assertThatThrownBy(() -> Version.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeParts() {
        assertThatThrownBy(() -> Version.of(1, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Version.of(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Version.of(1, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesMajorThenMinorThenPatch() {
        assertThat(Version.parse("3.5.9")).isLessThan(Version.parse("4.0.0"));
        assertThat(Version.parse("4.0.9")).isLessThan(Version.parse("4.1.0"));
        assertThat(Version.parse("4.1.0")).isLessThan(Version.parse("4.1.1"));
        assertThat(Version.parse("4.1")).isEqualByComparingTo(Version.parse("4.1.0"));
    }

    @Test
    void containsVersionsFromLowerBoundUpToButExcludingUpperBound() {
        VersionRange boot4 = VersionRange.between(Version.of(4, 0, 0), Version.of(5, 0, 0));

        assertThat(boot4.contains(Version.parse("4.0.0"))).isTrue();
        assertThat(boot4.contains(Version.parse("4.9.9"))).isTrue();
        assertThat(boot4.contains(Version.parse("5.0.0"))).isFalse();
        assertThat(boot4.contains(Version.parse("3.5.6"))).isFalse();
    }

    @Test
    void leavesOpenEndedRangesUnbounded() {
        VersionRange java21Plus = VersionRange.atLeast(Version.of(21, 0, 0));

        assertThat(java21Plus.contains(Version.parse("99"))).isTrue();
        assertThat(java21Plus.contains(Version.parse("17"))).isFalse();
    }

    @Test
    void rejectsEmptyRanges() {
        assertThatThrownBy(() -> VersionRange.between(Version.of(4, 0, 0), Version.of(4, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void printsRangesAsHalfOpenIntervals() {
        assertThat(VersionRange.between(Version.of(3, 3, 0), Version.of(4, 0, 0)))
                .hasToString("[3.3.0, 4.0.0)");
        assertThat(VersionRange.atLeast(Version.of(21, 0, 0))).hasToString("[21.0.0, )");
    }
}
