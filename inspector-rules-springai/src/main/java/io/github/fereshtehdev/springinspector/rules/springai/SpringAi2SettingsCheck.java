package io.github.fereshtehdev.springinspector.rules.springai;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.SettingsCheck;
import io.github.fereshtehdev.springinspector.javamodel.settings.Setting;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Settings the Spring AI 2.0 upgrade notes list as removed or deprecated, in a project on 2.0 or later. A removed
 * setting is ignored without an error, so the application silently runs with a different behaviour.
 */
public final class SpringAi2SettingsCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("migration.spring-ai-2-settings");

    private static final String INTERNAL_TOOL_EXECUTION = Setting.canonical("internal-tool-execution-enabled");
    private static final String SPRING_AI = "spring.ai.";
    private static final String REDIS_MEMORY = "spring.ai.chat.memory.redis.";
    private static final Pattern NESTED_OPTIONS =
            Pattern.compile("spring\\.ai\\.[^.]+\\.(?:chat|embedding|image|moderation|audio\\.[^.]+)\\.options\\..*");

    private static final Map<String, String> REMOVED = Map.of(
            "spring.ai.ollama.chat.think-option", "Use spring.ai.ollama.chat.think.",
            "spring.ai.chat.client.tool-calling.stream-tool-call-responses", "Remove this setting.",
            "spring.ai.chat.client.tool-search-advisor.stream-tool-call-responses", "Remove this setting.");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Spring Boot ignores settings that no longer exist, without an error, so what you configured here does not"
                    + " take effect. Deprecated settings still work, but will stop working in a later version.",
            List.of(new Reference(
                    "Spring AI upgrade notes",
                    URI.create("https://docs.spring.io/spring-ai/reference/upgrade-notes.html"))));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.MIGRATION;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringSpringAi(VersionRange.atLeast(Version.of(2, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(SettingsFile file) {
        List<Finding> findings = new ArrayList<>();
        for (Setting setting : file.settings()) {
            String canonical = Setting.canonical(setting.key());
            if (!setting.startsWith(SPRING_AI)) {
                continue;
            }
            if (canonical.endsWith(INTERNAL_TOOL_EXECUTION)) {
                findings.add(removed(file, setting, "Remove this setting; tools now run through ToolCallingAdvisor."));
            } else if (setting.startsWith(REDIS_MEMORY)) {
                findings.add(deprecated(file, setting, "Use the prefix spring.ai.chat.memory.repository.redis."));
            } else if (NESTED_OPTIONS.matcher(canonical).matches()) {
                findings.add(deprecated(file, setting, "Remove '.options' from the key."));
            } else {
                REMOVED.entrySet().stream()
                        .filter(entry -> setting.is(entry.getKey()))
                        .findFirst()
                        .ifPresent(entry -> findings.add(removed(file, setting, entry.getValue())));
            }
        }
        return findings;
    }

    private Finding removed(SettingsFile file, Setting setting, String fix) {
        return finding(
                file,
                setting,
                Severity.MAJOR,
                Confidence.HIGH,
                setting.key() + " was removed in Spring AI 2.0 and is ignored",
                WHY,
                HowToFix.withoutPatch(fix));
    }

    private Finding deprecated(SettingsFile file, Setting setting, String fix) {
        return finding(
                file,
                setting,
                Severity.MINOR,
                Confidence.HIGH,
                setting.key() + " is deprecated in Spring AI 2.0",
                WHY,
                HowToFix.withoutPatch(fix));
    }
}
