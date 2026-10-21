package io.github.fereshtehdev.springinspector.rules.security;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.SettingsCheck;
import io.github.fereshtehdev.springinspector.javamodel.settings.Setting;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Passwords, keys and tokens written into {@code application*} settings instead of coming from the environment.
 * Settings of development and test profiles are skipped; reported snippets are masked.
 */
public final class SecretInSettingsCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("security.secret-in-settings");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Settings files are committed with the code, so the secret is in every clone, image and backup, and in git"
                    + " history after the line is deleted. Production secrets belong in the environment or a secret"
                    + " store, where they can be rotated without a release.",
            List.of(
                    SecurityReferences.cwe(798, "Use of hard-coded credentials"),
                    SecurityReferences.springDocs(
                            "Spring Boot: Externalized configuration",
                            "spring-boot/reference/features/external-config.html")));

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
            String value = setting.value().strip();
            if (SettingsFile.isDevelopmentOnly(setting) || value.isEmpty() || setting.isPlaceholder()) {
                continue;
            }
            Optional<String> token = Secrets.knownTokenKind(value);
            String lastSegment = setting.key().substring(setting.key().lastIndexOf('.') + 1);
            if (token.isPresent()) {
                findings.add(report(
                        file, setting, Severity.BLOCKER, "A " + token.get() + " is written in " + setting.key()));
            } else if (Secrets.isSensitiveName(lastSegment) && Secrets.couldBeSecret(value)) {
                findings.add(report(file, setting, Severity.MAJOR, "A secret is written in " + setting.key()));
            }
        }
        return findings;
    }

    private Finding report(SettingsFile file, Setting setting, Severity severity, String what) {
        Finding finding = finding(file, setting, severity, Confidence.HIGH, what, WHY, fix(file, setting));
        String masked =
                Secrets.mask(finding.whatsWrong().snippet(), setting.value().strip());
        return new Finding(
                finding.ruleId(),
                finding.category(),
                finding.severity(),
                finding.location(),
                new WhatsWrong(finding.whatsWrong().description(), masked),
                finding.whyItMatters(),
                finding.howToFix(),
                finding.confidence(),
                finding.source());
    }

    /** Spring binds {@code SPRING_DATASOURCE_PASSWORD} to {@code spring.datasource.password} by itself. */
    private static HowToFix fix(SettingsFile file, Setting setting) {
        String variable = setting.key().replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
        String explanation = "Rotate the secret, then read it from the environment: set " + variable
                + " where the application runs and refer to it as ${" + variable + "}.";
        return file.replaceValue(setting, "${" + variable + "}")
                .map(patch -> HowToFix.withPatch(explanation, patch))
                .orElseGet(() -> HowToFix.withoutPatch(explanation));
    }
}
