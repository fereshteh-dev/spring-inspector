package io.github.fereshtehdev.springinspector.javamodel.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.javamodel.patch.Patches;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsParserTest {

    private static SettingsFile file(String name, String content) {
        SourceFile source = new SourceFile(Path.of("src", "main", "resources", name), content);
        return new SettingsFile(source, SettingsParser.parse(source), RuleOptions.none());
    }

    private static Setting only(SettingsFile file, String key) {
        List<Setting> found = file.find(key);
        assertThat(found).as(key).hasSize(1);
        return found.getFirst();
    }

    private static String replaced(SettingsFile file, Setting setting, String value) {
        TextEdit edit = new TextEdit(
                setting.valueStart().orElseThrow(), setting.valueEnd().orElseThrow(), value);
        return Patches.apply(file.source().content(), List.of(edit));
    }

    @Test
    void readsPropertiesWithEverySeparatorCommentsAndContinuations() {
        SettingsFile file = file("application.properties", """
                # comment
                ! also a comment
                server.port=8080
                spring.application.name: demo
                spring.h2.console.enabled  true
                spring.datasource.url = jdbc:h2:mem:test;\\
                    MODE=PostgreSQL
                app.key\\=with\\=equals=x
                app.escaped=line\\tbreak
                """);

        assertThat(only(file, "server.port").value()).isEqualTo("8080");
        assertThat(only(file, "server.port").line()).isEqualTo(3);
        assertThat(only(file, "spring.application.name").value()).isEqualTo("demo");
        assertThat(only(file, "spring.h2.console.enabled").value()).isEqualTo("true");
        assertThat(only(file, "spring.datasource.url").value()).isEqualTo("jdbc:h2:mem:test;MODE=PostgreSQL");
        assertThat(only(file, "spring.datasource.url").valueStart()).isEmpty();
        assertThat(only(file, "app.key=with=equals").value()).isEqualTo("x");
        assertThat(only(file, "app.escaped").value()).isEqualTo("line\tbreak");
    }

    @Test
    void flattensYamlIntoDottedKeysWithLines() {
        SettingsFile file = file("application.yml", """
                spring:
                  application:
                    name: demo
                management:
                  endpoints:
                    web:
                      exposure:
                        include:
                          - health
                          - env
                """);

        assertThat(only(file, "spring.application.name").line()).isEqualTo(3);
        assertThat(only(file, "management.endpoints.web.exposure.include[1]").value())
                .isEqualTo("env");
        assertThat(only(file, "management.endpoints.web.exposure.include[1]").line())
                .isEqualTo(10);
    }

    @Test
    void matchesKeysWithSpringsRelaxedBinding() {
        SettingsFile file = file("application.properties", "spring.h2.console.ENABLED=true\nserver.forwardHeaders=x\n");

        assertThat(file.find("spring.h2.console.enabled")).hasSize(1);
        assertThat(only(file, "server.forward-headers").startsWith("server.")).isTrue();
    }

    @Test
    void takesTheProfileFromTheFileNameOrTheDocument() {
        SettingsFile prod = file("application-prod.properties", "a=1\n");
        SettingsFile multi = file("application.yml", """
                a: 1
                ---
                spring:
                  config:
                    activate:
                      on-profile: dev
                a: 2
                """);
        SettingsFile properties =
                file("application.properties", "a=1\n#---\nspring.config.activate.on-profile=local\na=2\n");

        assertThat(only(prod, "a").profile()).contains("prod");
        assertThat(multi.find("a")).extracting(Setting::profile).containsExactly(Optional.empty(), Optional.of("dev"));
        assertThat(properties.find("a"))
                .extracting(Setting::profile)
                .containsExactly(Optional.empty(), Optional.of("local"));
    }

    @Test
    void recognisesDevelopmentProfiles() {
        assertThat(SettingsFile.isDevelopmentOnly(only(file("application-dev.yml", "a: 1"), "a")))
                .isTrue();
        assertThat(SettingsFile.isDevelopmentOnly(only(file("application-prod.yml", "a: 1"), "a")))
                .isFalse();
        assertThat(SettingsFile.isDevelopmentOnly(only(file("application.yml", "a: 1"), "a")))
                .isFalse();
        assertThat(SettingsFile.isDevelopmentOnly(new Setting(
                        "a",
                        "1",
                        1,
                        Optional.of("local & !prod"),
                        java.util.OptionalInt.empty(),
                        java.util.OptionalInt.empty())))
                .isTrue();
    }

    @Test
    void knowsWhichFilesAreSpringSettings() {
        assertThat(SettingsFile.isSettingsFile(Path.of("src/main/resources/application.yml")))
                .isTrue();
        assertThat(SettingsFile.isSettingsFile(Path.of("application-prod.properties")))
                .isTrue();
        assertThat(SettingsFile.isSettingsFile(Path.of("config/bootstrap.yaml")))
                .isTrue();
        assertThat(SettingsFile.isSettingsFile(Path.of("src/main/resources/logback.xml")))
                .isFalse();
        assertThat(SettingsFile.isSettingsFile(Path.of("my-application.yml"))).isFalse();
        assertThat(SettingsFile.profileOf(Path.of("application.yml"))).isEmpty();
    }

    @Test
    void locatesPlainValuesSoTheyCanBeReplaced() {
        SettingsFile yaml = file("application.yml", "spring:\n  h2:\n    console:\n      enabled: true\n");
        SettingsFile properties = file("application.properties", "spring.h2.console.enabled = true \n");
        SettingsFile quoted = file("application.yml", "a: \"quoted\"\n");

        assertThat(replaced(yaml, only(yaml, "spring.h2.console.enabled"), "false"))
                .endsWith("enabled: false\n");
        assertThat(replaced(properties, only(properties, "spring.h2.console.enabled"), "false"))
                .isEqualTo("spring.h2.console.enabled = false \n");
        assertThat(only(quoted, "a").valueStart()).isEmpty();
        assertThat(quoted.replaceValue(only(quoted, "a"), "x")).isEmpty();
        assertThat(yaml.replaceValue(only(yaml, "spring.h2.console.enabled"), "false"))
                .isPresent();
    }

    @Test
    void knowsPlaceholders() {
        SettingsFile file = file("application.properties", "a=${SECRET}\nb=literal\n");

        assertThat(only(file, "a").isPlaceholder()).isTrue();
        assertThat(only(file, "b").isPlaceholder()).isFalse();
    }

    @Test
    void sharedYamlAliasesCannotMultiplyIntoMillionsOfSettings() {
        StringBuilder bomb = new StringBuilder("a: &a [x, x, x, x, x, x, x, x, x, x]\n");
        for (char level = 'b'; level <= 'h'; level++) {
            char previous = (char) (level - 1);
            bomb.append(level).append(": &").append(level).append(" [");
            bomb.append(("*" + previous + ", ").repeat(9))
                    .append('*')
                    .append(previous)
                    .append("]\n");
        }

        assertThatThrownBy(() -> file("application.yml", bomb.toString())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidYaml() {
        assertThatThrownBy(() -> file("application.yml", "a: [unclosed\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not valid YAML");
    }

    @Test
    void reportsLocationsAndSnippetsOfSettings() {
        SettingsFile file = file("application.properties", "a=1\nb=2\n");
        Setting b = only(file, "b");

        assertThat(file.location(b).lines().start()).isEqualTo(2);
        assertThat(file.snippet(b)).isEqualTo("b=2");
        assertThat(file.path()).isEqualTo(Path.of("src", "main", "resources", "application.properties"));
        assertThat(file.withOptions(new RuleOptions(java.util.Map.of("x", "1")))
                        .options()
                        .fingerprint())
                .isEqualTo("x=1");
        assertThat(file.settings()).hasSize(2);
    }
}
