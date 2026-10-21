package io.github.fereshtehdev.springinspector.javamodel;

import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.PROJECT;
import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.input;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.Fixtures.Sources;
import io.github.fereshtehdev.springinspector.javamodel.cache.CacheSecret;
import io.github.fereshtehdev.springinspector.javamodel.cache.FindingCache;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsCheckTest {

    private static final Path SETTINGS = Path.of("src", "main", "resources", "application.properties");
    private static final Path JAVA = Fixtures.main("App.java");

    @TempDir
    Path project;

    /** Reports every setting whose key starts with {@code secret}. */
    private static final class SecretSettings extends SettingsCheck {

        private final AtomicInteger inspected = new AtomicInteger();

        @Override
        public RuleId id() {
            return RuleId.of("test.secret-settings");
        }

        @Override
        public Category category() {
            return Category.SECURITY;
        }

        @Override
        protected List<Finding> inspect(SettingsFile file) {
            inspected.incrementAndGet();
            return file.settings().stream()
                    .filter(setting -> setting.startsWith("secret"))
                    .map(setting -> finding(
                            file,
                            setting,
                            Severity.MAJOR,
                            Confidence.HIGH,
                            setting.key(),
                            new WhyItMatters("Test", List.of()),
                            HowToFix.withoutPatch("Test")))
                    .toList();
        }
    }

    private final Sources sources =
            new Sources().put(SETTINGS, "secret.key=abc\nserver.port=8080\n").put(JAVA, "class App {}");

    @Test
    void inspectsOnlySpringSettingsFiles() {
        SecretSettings check = new SecretSettings();

        List<Finding> findings = check.inspect(input(new JavaModel(PROJECT, sources), SETTINGS, JAVA));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.whatsWrong().description()).isEqualTo("secret.key");
            assertThat(finding.whatsWrong().snippet()).isEqualTo("secret.key=abc");
            assertThat(finding.location().lines().start()).isEqualTo(1);
        });
        assertThat(check.inspected.get()).isEqualTo(1);
    }

    @Test
    void reusesCachedResultsForUnchangedSettings() {
        CacheSecret secret = CacheSecret.random();
        FindingCache first = FindingCache.open(project, secret);
        new SecretSettings()
                .inspect(input(new JavaModel(PROJECT, sources, first, check -> Optional.of("same")), SETTINGS));
        first.save();

        SecretSettings again = new SecretSettings();
        List<Finding> findings = again.inspect(input(
                new JavaModel(PROJECT, sources, FindingCache.open(project, secret), check -> Optional.of("same")),
                SETTINGS));

        assertThat(findings).hasSize(1);
        assertThat(again.inspected.get()).isZero();
    }

    @Test
    void anUnparseableSettingsFileIsReportedNotInspected() {
        Path yaml = Path.of("src", "main", "resources", "application.yml");
        sources.put(yaml, "a: [unclosed\n");
        JavaModel model = new JavaModel(PROJECT, sources);

        assertThat(new SecretSettings().inspect(input(model, yaml))).isEmpty();
        assertThat(model.unparseableFiles()).containsOnlyKeys(yaml);
    }
}
