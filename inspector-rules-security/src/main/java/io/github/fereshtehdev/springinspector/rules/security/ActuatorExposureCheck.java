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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Actuator endpoints that reveal secrets or change the running application, exposed over HTTP outside development
 * profiles: {@code include=*}, endpoints such as {@code env} or {@code heapdump}, or an enabled {@code shutdown}.
 */
public final class ActuatorExposureCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("security.actuator-exposed");

    private static final String INCLUDE = "management.endpoints.web.exposure.include";
    private static final Set<String> SENSITIVE_ENDPOINTS = Set.of(
            "env",
            "heapdump",
            "threaddump",
            "configprops",
            "beans",
            "mappings",
            "loggers",
            "shutdown",
            "jolokia",
            "sessions",
            "logfile",
            "httpexchanges");

    private static final WhyItMatters WHY = new WhyItMatters(
            "These endpoints show environment variables, configuration and memory contents, which include passwords"
                    + " and keys, or let a caller change log levels and shut the application down. Exposed without"
                    + " authentication, they hand attackers the keys.",
            List.of(
                    SecurityReferences.cwe(200, "Exposure of sensitive information"),
                    SecurityReferences.springDocs(
                            "Spring Boot: Actuator endpoints",
                            "spring-boot/reference/actuator/endpoints.html#actuator.endpoints.exposing")));

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
            if (setting.is(INCLUDE) || setting.startsWith(INCLUDE + "[")) {
                exposure(file, setting).ifPresent(findings::add);
            } else if (setting.is("management.endpoint.shutdown.enabled")
                    && setting.value().equalsIgnoreCase("true")) {
                findings.add(finding(
                        file,
                        setting,
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "The shutdown endpoint is enabled",
                        WHY,
                        fix(file, setting, "false", "Disable the shutdown endpoint.")));
            }
        }
        return findings;
    }

    private Optional<Finding> exposure(SettingsFile file, Setting setting) {
        Set<String> exposed = new TreeSet<>();
        for (String endpoint : setting.value().split(",", -1)) {
            String name = endpoint.strip().toLowerCase(Locale.ROOT);
            if (name.equals("*") || SENSITIVE_ENDPOINTS.contains(name)) {
                exposed.add(name);
            }
        }
        if (exposed.isEmpty()) {
            return Optional.empty();
        }
        boolean all = exposed.contains("*");
        String what = all ? "every actuator endpoint" : "actuator endpoints " + String.join(", ", exposed);
        HowToFix fix = all && setting.is(INCLUDE)
                ? fix(file, setting, "health,info", "Expose only what monitoring needs.")
                : HowToFix.withoutPatch("Expose only what monitoring needs, usually health and info.");
        return Optional.of(finding(
                file, setting, Severity.MAJOR, Confidence.MEDIUM, "Settings expose " + what + " over HTTP", WHY, fix));
    }

    private static HowToFix fix(SettingsFile file, Setting setting, String value, String advice) {
        String explanation = advice + " If other endpoints are needed, protect them with Spring Security or serve"
                + " them on a separate management port that is not reachable from outside.";
        return file.replaceValue(setting, value)
                .map(patch -> HowToFix.withPatch(explanation, patch))
                .orElseGet(() -> HowToFix.withoutPatch(explanation));
    }
}
