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

/** Session cookies explicitly set to be readable by scripts or sent without TLS, outside development profiles. */
public final class InsecureSessionCookieCheck extends SettingsCheck {

    static final RuleId ID = RuleId.of("security.insecure-session-cookie");

    private static final String HTTP_ONLY = "server.servlet.session.cookie.http-only";
    private static final String SECURE = "server.servlet.session.cookie.secure";

    private static final WhyItMatters WHY = new WhyItMatters(
            "A session cookie is as good as the user's password. Scripts injected through a cross-site scripting bug"
                    + " can steal one that is not HttpOnly, and anyone on the network can read one that is not Secure"
                    + " when it travels without TLS.",
            List.of(
                    SecurityReferences.cwe(1004, "Sensitive cookie without the HttpOnly flag"),
                    SecurityReferences.cwe(614, "Sensitive cookie without the Secure attribute"),
                    SecurityReferences.owaspCheatSheet("Session Management", "Session_Management_Cheat_Sheet")));

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
            boolean turnedOff = setting.value().equalsIgnoreCase("false");
            if (!turnedOff || SettingsFile.isDevelopmentOnly(setting)) {
                continue;
            }
            if (setting.is(HTTP_ONLY)) {
                findings.add(report(file, setting, Confidence.HIGH, "Scripts in the page can read the session cookie"));
            } else if (setting.is(SECURE)) {
                findings.add(report(
                        file, setting, Confidence.MEDIUM, "The session cookie can be sent over unencrypted HTTP"));
            }
        }
        return findings;
    }

    private Finding report(SettingsFile file, Setting setting, Confidence confidence, String whatsWrong) {
        String explanation = "Keep this setting on. Only a local development profile should turn it off.";
        HowToFix fix = file.replaceValue(setting, "true")
                .map(patch -> HowToFix.withPatch(explanation, patch))
                .orElseGet(() -> HowToFix.withoutPatch(explanation));
        return finding(file, setting, Severity.MAJOR, confidence, whatsWrong, WHY, fix);
    }
}
