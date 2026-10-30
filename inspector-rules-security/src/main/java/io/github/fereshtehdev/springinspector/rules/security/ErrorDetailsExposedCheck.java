package io.github.fereshtehdev.springinspector.rules.security;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.SettingsCheck;
import io.github.fereshtehdev.springinspector.javamodel.settings.Setting;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
import java.util.ArrayList;
import java.util.List;

/** Error responses that always show stack traces, exception types or internal messages, outside development profiles. */
public final class ErrorDetailsExposedCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("security.error-details-exposed");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Error details tell an attacker which libraries and versions you run, what your classes and queries look"
                    + " like, and sometimes contain the data being processed. This helps them plan their next attack.",
            List.of(
                    SecurityReferences.cwe(209, "Sensitive information in error messages"),
                    SecurityReferences.springDocs(
                            "Spring Boot: Error handling",
                            "spring-boot/reference/web/servlet.html#web.servlet.spring-mvc.error-handling")));

    private record Exposure(String key, String unsafeValue, String safeValue, Severity severity, String what) {}

    private static final List<Exposure> EXPOSURES = List.of(
            new Exposure("server.error.include-stacktrace", "always", "never", Severity.MAJOR, "stack traces"),
            new Exposure("server.error.include-exception", "true", "false", Severity.MAJOR, "exception class names"),
            new Exposure("server.error.include-message", "always", "never", Severity.MINOR, "exception messages"),
            new Exposure(
                    "server.error.include-binding-errors", "always", "never", Severity.MINOR, "validation details"));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected List<Finding> inspect(SettingsFile file) {
        List<Finding> findings = new ArrayList<>();
        for (Setting setting : file.settings()) {
            if (SettingsFile.isDevelopmentOnly(setting)) {
                continue;
            }
            EXPOSURES.stream()
                    .filter(exposure ->
                            setting.is(exposure.key()) && setting.value().equalsIgnoreCase(exposure.unsafeValue()))
                    .forEach(exposure -> findings.add(finding(
                            file,
                            setting,
                            exposure.severity(),
                            Confidence.HIGH,
                            "Error responses show " + exposure.what() + " to every caller",
                            WHY,
                            fix(file, setting, exposure.safeValue()))));
        }
        return findings;
    }

    private static HowToFix fix(SettingsFile file, Setting setting, String value) {
        String explanation = "Keep the details in the server's logs and return only a general message to callers."
                + " If you need them while developing, set this in a development profile.";
        return file.replaceValue(setting, value)
                .map(patch -> HowToFix.withPatch(explanation, patch))
                .orElseGet(() -> HowToFix.withoutPatch(explanation));
    }
}
