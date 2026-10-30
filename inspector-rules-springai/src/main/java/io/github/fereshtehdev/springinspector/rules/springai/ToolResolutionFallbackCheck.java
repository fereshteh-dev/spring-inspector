package io.github.fereshtehdev.springinspector.rules.springai;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.SettingsCheck;
import io.github.fereshtehdev.springinspector.javamodel.settings.Setting;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
import java.net.URI;
import java.util.List;

/** {@code spring.ai.tools.resolution.fallback.enabled=true}, which Spring AI 2.0.1 turned off for safety. */
public final class ToolResolutionFallbackCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("springai.tool-resolution-fallback");

    private static final String FALLBACK = "spring.ai.tools.resolution.fallback.enabled";

    private static final WhyItMatters WHY = new WhyItMatters(
            "With this setting, a tool the model names is looked up across the whole application, even if it was not"
                    + " attached to the request. Prompt injection can then reach tools meant for other features or"
                    + " other users.",
            List.of(
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM06 Excessive Agency",
                            URI.create("https://genai.owasp.org/llmrisk/llm062025-excessive-agency/")),
                    new Reference(
                            "Spring AI: Upgrade notes",
                            URI.create("https://docs.spring.io/spring-ai/reference/upgrade-notes.html"))));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_AI;
    }

    @Override
    protected List<Finding> inspect(SettingsFile file) {
        return file.settings().stream()
                .filter(setting -> setting.is(FALLBACK) && setting.value().equalsIgnoreCase("true"))
                .filter(setting -> !SettingsFile.isDevelopmentOnly(setting))
                .map(setting -> finding(
                        file,
                        setting,
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "The model may call tools that were not attached to the request",
                        WHY,
                        fix(file, setting)))
                .toList();
    }

    private static HowToFix fix(SettingsFile file, Setting setting) {
        String explanation = "Turn the fallback off and attach the tools each request needs with .tools(...).";
        return file.replaceValue(setting, "false")
                .map(patch -> HowToFix.withPatch(explanation, patch))
                .orElseGet(() -> HowToFix.withoutPatch(explanation));
    }
}
