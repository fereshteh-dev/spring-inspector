package io.github.fereshtehdev.springinspector.project.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.PathPattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfigFileReaderTest {

    private static final String FULL = """
            gate:
              block-on: major
              minimum-score: 70
            categories:
              disabled: [clean-code, SPRING_AI]
            rules:
              disabled: [spring.field-injection]
              severity:
                security.weak-cryptography: Blocker
              options:
                cleancode.long-method:
                  max-lines: 80
            ignore:
              - "**/generated/**"
            max-findings-per-category: 5
            check-timeout: 2m
            changed-lines-only: false
            """;

    @TempDir
    Path project;

    private final ConfigFileReader reader = new ConfigFileReader();

    private InspectorConfig parse(String yaml) {
        return reader.parse(yaml, "spring-inspector.yml").config();
    }

    private void assertRejected(String yaml, String message) {
        assertThatThrownBy(() -> parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @Test
    void readsEverySetting() {
        InspectorConfig config = parse(FULL);

        assertThat(config.gatePolicy().blockOn()).isEqualTo(Severity.MAJOR);
        assertThat(config.gatePolicy().minimumScore()).isEqualTo(OptionalInt.of(70));
        assertThat(config.disabledCategories()).containsExactlyInAnyOrder(Category.CLEAN_CODE, Category.SPRING_AI);
        assertThat(config.disabledRules()).containsExactly(RuleId.of("spring.field-injection"));
        assertThat(config.severityOverrides()).containsEntry(RuleId.of("security.weak-cryptography"), Severity.BLOCKER);
        assertThat(config.options(RuleId.of("cleancode.long-method")).positiveInteger("max-lines"))
                .contains(80);
        assertThat(config.ignoredPaths()).containsExactly(PathPattern.of("**/generated/**"));
        assertThat(config.maxFindingsPerCategory()).isEqualTo(5);
        assertThat(config.checkTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.changedLinesOnly()).isFalse();
    }

    @Test
    void anEmptyFileMeansTheDefaults() {
        assertThat(parse("")).isEqualTo(InspectorConfig.defaults());
        assertThat(parse("# only a comment\n")).isEqualTo(InspectorConfig.defaults());
        assertThat(parse("gate: {}\nrules:\n")).isEqualTo(InspectorConfig.defaults());
    }

    @Test
    void unknownSettingsAreWarningsSoNewerFilesStillWork() {
        LoadedConfig loaded = reader.parse("""
                tone: sensei
                gate:
                  block-on: blocker
                  soon: true
                """, "spring-inspector.yml");

        assertThat(loaded.warnings())
                .containsExactly(
                        "spring-inspector.yml: unknown setting 'tone' is ignored",
                        "spring-inspector.yml: unknown setting 'gate.soon' is ignored");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"5s|PT5S", "500ms|PT0.5S", "3|PT3S", "1 m|PT1M"})
    void readsDurationsWithUnits(String value, Duration expected) {
        assertThat(parse("check-timeout: " + value).checkTimeout()).isEqualTo(expected);
    }

    @Test
    void namesTheSettingWhenAValueIsWrong() {
        assertRejected("gate:\n  block-on: critical", "gate.block-on must be one of blocker, major, minor, info");
        assertRejected("gate:\n  block-on: [major]", "gate.block-on must be text");
        assertRejected("gate:\n  minimum-score: 120", "gate.minimum-score must be a whole number from 0 to 100");
        assertRejected("gate:\n  minimum-score: high", "gate.minimum-score must be a whole number");
        assertRejected("gate: strict", "gate must be a mapping of settings");
        assertRejected("categories:\n  disabled: [style]", "categories.disabled unknown category 'style'");
        assertRejected("categories:\n  disabled: clean-code", "categories.disabled must be a list");
        assertRejected("rules:\n  disabled: [1]", "rules.disabled must be a list of text values");
        assertRejected("rules:\n  disabled: ['not a rule']", "rules.disabled 'not a rule' is not a rule id");
        assertRejected("rules:\n  severity:\n    a.b: [x]", "rules.severity.a.b must be one of");
        assertRejected("rules:\n  options:\n    a.b: 3", "rules.options.a.b must be a mapping");
        assertRejected("rules:\n  options:\n    a.b:\n      x: [1]", "rules.options.a.b.x must be a single value");
        assertRejected("check-timeout: 0s", "check-timeout must be a positive duration");
        assertRejected("check-timeout: soon", "check-timeout must be a positive duration");
        assertRejected(
                "max-findings-per-category: 0", "max-findings-per-category must be a whole number of at least 1");
        assertRejected("changed-lines-only: sometimes", "changed-lines-only must be true or false");
        assertRejected("- just\n- a list", "the file must be a mapping of settings");
    }

    @Test
    void reportsYamlErrorsWithTheirLine() {
        assertRejected("gate:\n  block-on: [major\n", "is not valid YAML (line");
    }

    @Test
    void refusesDuplicateKeys() {
        assertRejected("gate: {}\ngate: {}\n", "not valid YAML");
    }

    @Test
    void doesNotExpandAliasesSoAnAliasBombStaysSmall() {
        StringBuilder bomb = new StringBuilder("a: &a [x, x, x, x, x, x, x, x, x, x]\n");
        for (char level = 'b'; level <= 'j'; level++) {
            char previous = (char) (level - 1);
            bomb.append(level).append(": &").append(level).append(" [");
            bomb.append(("*" + previous + ", ").repeat(9))
                    .append('*')
                    .append(previous)
                    .append("]\n");
        }

        LoadedConfig loaded = reader.parse(bomb.toString(), "spring-inspector.yml");

        assertThat(loaded.warnings()).hasSize(10);
        assertThat(loaded.config()).isEqualTo(InspectorConfig.defaults());
    }

    @Test
    void usesTheDefaultsWhenTheProjectHasNoFile() {
        LoadedConfig loaded = reader.read(project);

        assertThat(loaded.config()).isEqualTo(InspectorConfig.defaults());
        assertThat(loaded.file()).isEmpty();
    }

    @Test
    void readsTheProjectsFile() throws IOException {
        Path file = Files.writeString(project.resolve(ConfigFileReader.FILE_NAME), FULL);

        LoadedConfig loaded = reader.read(project);

        assertThat(loaded.config().maxFindingsPerCategory()).isEqualTo(5);
        assertThat(loaded.file()).contains(file);
    }

    @Test
    void refusesAFileThatIsTooLarge() throws IOException {
        Files.writeString(project.resolve(ConfigFileReader.FILE_NAME), "# " + "x".repeat(ConfigFileReader.MAX_BYTES));

        assertThatThrownBy(() -> reader.read(project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger than 64 KiB");
    }

    @Test
    void refusesADirectoryInPlaceOfTheFile() throws IOException {
        Files.createDirectory(project.resolve(ConfigFileReader.FILE_NAME));

        assertThatThrownBy(() -> reader.read(project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a regular file");
    }

    @Test
    void readsAnExplicitlyNamedFile() throws IOException {
        Path ci = Files.writeString(project.resolve("ci.yml"), "gate:\n  block-on: major\n");

        assertThat(reader.readFile(ci).config().gatePolicy().blockOn()).isEqualTo(Severity.MAJOR);
        assertThatThrownBy(() -> reader.readFile(project.resolve("missing.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration file not found");
    }
}
