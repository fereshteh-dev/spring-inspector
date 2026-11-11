package io.github.fereshtehdev.springinspector.document.design;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.document.SampleProject;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.RuntimeSettings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsReaderTest {

    @Test
    void readsNamePortStoresProfilesAndModels() {
        RuntimeSettings settings = SampleProject.shop().design().settings();

        assertThat(settings.applicationName()).contains("shop-service");
        assertThat(settings.serverPort()).contains("8080");
        assertThat(settings.dataStores()).containsExactly("PostgreSQL", "Redis");
        assertThat(settings.profiles()).containsExactly("prod");
        assertThat(settings.vectorStores()).containsExactly("pgvector");
    }

    @Test
    void neverCopiesAddressesOrCredentials() {
        RuntimeSettings settings = SampleProject.shop().design().settings();

        assertThat(settings.toString())
                .doesNotContain("hunter2")
                .doesNotContain("sk-test")
                .doesNotContain("db.internal")
                .doesNotContain("cache.internal");
    }

    @Test
    void namesTheDatabaseOfAnAddressHiddenInAPlaceholder() {
        assertThat(read("spring.datasource.url=${DB_URL:jdbc:mysql://localhost/app}")
                        .dataStores())
                .containsExactly("MySQL");
        assertThat(read("spring.datasource.url=${DB_URL}").dataStores()).containsExactly("SQL database");
        assertThat(read("spring.r2dbc.url=r2dbc:pool:mariadb://localhost/app").dataStores())
                .containsExactly("MariaDB");
    }

    @Test
    void saysWhenAModelIsChosenByTheEnvironment() {
        assertThat(read("spring.ai.anthropic.chat.options.model=${MODEL}").aiModels())
                .containsExactly("Anthropic chat model: set by the environment");
    }

    @Test
    void leavesOutValuesThatAreNotPlainNames() {
        RuntimeSettings settings = read("spring.application.name=<script>alert(1)</script>\nserver.port=${PORT}");

        assertThat(settings.applicationName()).isEmpty();
        assertThat(settings.serverPort()).isEmpty();
    }

    @Test
    void prefersTheSettingThatAppliesToEveryProfile() {
        RuntimeSettings settings = SettingsReader.of(List.of(
                new SourceFile(Path.of("src/main/resources/application-dev.properties"), "server.port=9000"),
                new SourceFile(Path.of("src/main/resources/application.properties"), "server.port=8080")));

        assertThat(settings.serverPort()).contains("8080");
    }

    @Test
    void survivesAFileThatIsNotValidYaml() {
        RuntimeSettings settings = SettingsReader.of(
                List.of(new SourceFile(Path.of("src/main/resources/application.yml"), "a: [unclosed\n  b: {")));

        assertThat(settings).isEqualTo(RuntimeSettings.none());
    }

    private static RuntimeSettings read(String properties) {
        return SettingsReader.of(
                List.of(new SourceFile(Path.of("src/main/resources/application.properties"), properties)));
    }
}
