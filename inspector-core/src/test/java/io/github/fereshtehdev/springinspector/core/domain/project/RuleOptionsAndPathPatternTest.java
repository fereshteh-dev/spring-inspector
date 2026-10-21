package io.github.fereshtehdev.springinspector.core.domain.project;

import static io.github.fereshtehdev.springinspector.core.testing.FindingBuilder.aFinding;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.report.SuppressedFinding;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleOptionsAndPathPatternTest {

    @Test
    void readsPositiveWholeNumbers() {
        RuleOptions options = new RuleOptions(Map.of("max-lines", " 80 "));

        assertThat(options.positiveInteger("max-lines")).contains(80);
        assertThat(options.positiveInteger("missing")).isEmpty();
        assertThat(RuleOptions.none().positiveInteger("max-lines")).isEmpty();
    }

    @Test
    void rejectsValuesThatAreNotPositiveWholeNumbers() {
        assertThatThrownBy(() -> new RuleOptions(Map.of("max-lines", "lots")).positiveInteger("max-lines"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option 'max-lines' must be a positive whole number, got 'lots'");
        assertThatThrownBy(() -> new RuleOptions(Map.of("max-lines", "0")).positiveInteger("max-lines"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFingerprintIgnoresTheOrderOfOptions() {
        RuleOptions options = new RuleOptions(Map.of("b", "2", "a", "1"));

        assertThat(options.fingerprint()).isEqualTo("a=1;b=2");
        assertThat(RuleOptions.none().fingerprint()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "**/generated/**, generated/A.java, true",
        "**/generated/**, src/main/generated/x/A.java, true",
        "**/generated/**, src/generatedStuff/A.java, false",
        "src/*.java, src/A.java, true",
        "src/*.java, src/a/A.java, false",
        "src/**, src/a/b/A.java, true",
        "**/*Test.java, src/test/java/ATest.java, true",
        "A?.java, AB.java, true",
        "A?.java, A/.java, false",
        "a.b, axb, false"
    })
    void matchesProjectRelativePathsLikeGitignore(String glob, String path, boolean matches) {
        assertThat(PathPattern.of(glob).matches(Path.of(path))).isEqualTo(matches);
    }

    @Test
    void patternsAreValuesNamedByTheirGlob() {
        assertThat(PathPattern.of(" a/** "))
                .isEqualTo(PathPattern.of("a/**"))
                .hasSameHashCodeAs(PathPattern.of("a/**"));
        assertThat(PathPattern.of("a/**").glob()).isEqualTo("a/**");
        assertThat(PathPattern.of("a/**")).hasToString("a/**").isNotEqualTo("a/**");
        assertThatThrownBy(() -> PathPattern.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theConfigAnswersOptionsAndIgnoredPaths() {
        InspectorConfig config = new InspectorConfig(
                Set.of(),
                Set.of(),
                Map.of(),
                Duration.ofSeconds(5),
                20,
                true,
                GatePolicy.defaults(),
                Map.of(RuleId.of("cleancode.long-method"), new RuleOptions(Map.of("max-lines", "80"))),
                List.of(PathPattern.of("**/generated/**")));

        assertThat(config.options(RuleId.of("cleancode.long-method")).positiveInteger("max-lines"))
                .contains(80);
        assertThat(config.options(RuleId.of("other"))).isEqualTo(RuleOptions.none());
        assertThat(config.isIgnored(Path.of("target", "generated", "A.java"))).isTrue();
        assertThat(InspectorConfig.defaults().isIgnored(Path.of("A.java"))).isFalse();
    }

    @Test
    void aSuppressionNeedsAReason() {
        assertThatThrownBy(() -> new SuppressedFinding(aFinding().build(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
