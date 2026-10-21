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

/**
 * The H2 web console enabled outside development and test profiles, and worse, reachable from other machines.
 */
public final class H2ConsoleEnabledCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("security.h2-console-enabled");

    private static final WhyItMatters WHY = new WhyItMatters(
            "The H2 console is a database client in the browser. Whoever reaches it can read and change all data, and"
                    + " through H2's features run code on the server. It is meant for local development only.",
            List.of(
                    SecurityReferences.cwe(489, "Active debug code"),
                    SecurityReferences.springDocs(
                            "Spring Boot: H2 console", "spring-boot/reference/data/sql.html#data.sql.h2-web-console")));

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
            if (SettingsFile.isDevelopmentOnly(setting)
                    || !setting.value().strip().equalsIgnoreCase("true")) {
                continue;
            }
            if (setting.is("spring.h2.console.enabled")) {
                findings.add(finding(
                        file,
                        setting,
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "The H2 console is enabled outside a development profile",
                        WHY,
                        fix(file, setting, "Turn it off here and enable it only in application-dev.")));
            } else if (setting.is("spring.h2.console.settings.web-allow-others")) {
                findings.add(finding(
                        file,
                        setting,
                        Severity.BLOCKER,
                        Confidence.HIGH,
                        "The H2 console accepts connections from other machines",
                        WHY,
                        fix(file, setting, "Never allow remote access to the H2 console.")));
            }
        }
        return findings;
    }

    private static HowToFix fix(SettingsFile file, Setting setting, String explanation) {
        return file.replaceValue(setting, "false")
                .map(patch -> HowToFix.withPatch(explanation, patch))
                .orElseGet(() -> HowToFix.withoutPatch(explanation));
    }
}
