package io.github.fereshtehdev.springinspector.intellij.run;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleaseVersionsTest {

    @Test
    void readsTheNewestReleaseFromGitHub() {
        assertThat(ReleaseVersions.latest("{\"url\": \"x\", \"tag_name\": \"v0.3.0\", \"name\": \"Spring Inspector\"}"))
                .contains("0.3.0");
        assertThat(ReleaseVersions.latest("{\"tag_name\":\"1.2\"}")).contains("1.2");
        assertThat(ReleaseVersions.latest("{\"message\": \"Not Found\"}")).isEmpty();
        assertThat(ReleaseVersions.latest("{\"tag_name\": \"v<script>\"}")).isEmpty();
    }

    @Test
    void comparesReleasesNumerically() {
        assertThat(ReleaseVersions.isNewer("0.10.0", "0.9.3")).isTrue();
        assertThat(ReleaseVersions.isNewer("1.0", "0.9.9")).isTrue();
        assertThat(ReleaseVersions.isNewer("0.2.1", "0.2.1")).isFalse();
        assertThat(ReleaseVersions.isNewer("0.2.0", "0.2.1")).isFalse();
        assertThat(ReleaseVersions.isNewer("next", "0.2.1")).isFalse();
    }

    @Test
    void neverTellsDevelopmentBuilds() {
        assertThat(ReleaseVersions.isNewer("9.0.0", "0.2.0-SNAPSHOT")).isFalse();
    }

    @Test
    void knowsItsOwnVersionFromTheBuild() {
        assertThat(ReleaseVersions.current()).hasValueSatisfying(version -> assertThat(version).matches("\\d+\\.\\d+.*"));
    }
}
