package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MavenVersionTest {

    @ParameterizedTest(name = "{0} < {1}")
    @CsvSource({
        "1.0, 1.1",
        "1.9, 1.10",
        "1.0-alpha1, 1.0-beta1",
        "1.0-beta2, 1.0-M1",
        "1.0-M3, 1.0-RC1",
        "1.0-RC1, 1.0-SNAPSHOT",
        "1.0-SNAPSHOT, 1.0",
        "1.0, 1.0-sp1",
        "1.0-sp1, 1.0-custom",
        "1.0-custom, 1.0.1",
        "1.0-alpha, 1.0-zzz",
        "1.0-abc, 1.0-abd",
        "2.17.0, 2.17.0.1",
        "5.3.39, 6.0.0-M1",
        "1.0.0-rc1, 1.0.0",
        "99999999999999999999, 100000000000000000000"
    })
    void ordersVersionsTheWayMavenDoes(String lower, String higher) {
        assertThat(MavenVersion.parse(lower)).isLessThan(MavenVersion.parse(higher));
        assertThat(MavenVersion.parse(higher)).isGreaterThan(MavenVersion.parse(lower));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "1, 1.0.0",
        "1.0, 1.0.RELEASE",
        "1.0, 1.0-GA",
        "1.0, 1.0.Final",
        "1.0-rc1, 1.0-cr1",
        "1.0-a1, 1.0-alpha-1",
        "1.0.0, 1.0-0",
        "2.0_1, 2.0.1"
    })
    void treatsEquivalentSpellingsAsEqual(String one, String other) {
        MavenVersion a = MavenVersion.parse(one);
        MavenVersion b = MavenVersion.parse(other);

        assertThat(a).isEqualByComparingTo(b).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void keepsTheOriginalSpellingForDisplay() {
        assertThat(MavenVersion.parse(" 6.1.0.RELEASE ")).hasToString("6.1.0.RELEASE");
    }

    @Test
    void isNotEqualToOtherTypes() {
        assertThat(MavenVersion.parse("1.0")).isNotEqualTo("1.0");
    }
}
