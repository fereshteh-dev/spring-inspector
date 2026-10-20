package io.github.fereshtehdev.springinspector.core.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApplicabilityTest {

    private static final VersionRange JAVA_21_PLUS = VersionRange.atLeast(Version.of(21, 0, 0));
    private static final VersionRange BOOT_4 = VersionRange.between(Version.of(4, 0, 0), Version.of(5, 0, 0));
    private static final VersionRange SPRING_AI_2 = VersionRange.between(Version.of(2, 0, 0), Version.of(3, 0, 0));

    private static DetectedVersions versions(String java, String boot, String springAi) {
        return new DetectedVersions(parse(java), parse(boot), parse(springAi));
    }

    private static Optional<Version> parse(String version) {
        return version.isEmpty() ? Optional.empty() : Optional.of(Version.parse(version));
    }

    @Test
    void appliesAlwaysEvenWhenNothingWasDetected() {
        assertThat(Applicability.always().appliesTo(DetectedVersions.none())).isTrue();
    }

    @Test
    void appliesWhenEveryRequiredVersionIsInRange() {
        Applicability rule = Applicability.always()
                .requiringJava(JAVA_21_PLUS)
                .requiringSpringBoot(BOOT_4)
                .requiringSpringAi(SPRING_AI_2);

        assertThat(rule.appliesTo(versions("25", "4.1.1", "2.0.1"))).isTrue();
    }

    @Test
    void doesNotApplyWhenARequiredVersionIsOutOfRange() {
        assertThat(Applicability.always().requiringJava(JAVA_21_PLUS).appliesTo(versions("17", "", "")))
                .isFalse();
        assertThat(Applicability.always().requiringSpringBoot(BOOT_4).appliesTo(versions("25", "3.5.6", "")))
                .isFalse();
        assertThat(Applicability.always().requiringSpringAi(SPRING_AI_2).appliesTo(versions("25", "4.1.1", "1.1.2")))
                .isFalse();
    }

    @Test
    void doesNotApplyWhenARequiredLibraryIsAbsent() {
        Applicability springAiRule = Applicability.always().requiringSpringAi(SPRING_AI_2);

        assertThat(springAiRule.appliesTo(versions("25", "4.1.1", ""))).isFalse();
    }

    @Test
    void ignoresVersionsTheRuleDoesNotConstrain() {
        Applicability bootRule = Applicability.always().requiringSpringBoot(BOOT_4);

        assertThat(bootRule.appliesTo(versions("", "4.0.0", ""))).isTrue();
    }
}
