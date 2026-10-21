package io.github.fereshtehdev.springinspector.javamodel;

import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.javamodel.settings.Setting;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsFile;
import java.util.ArrayList;
import java.util.List;

/**
 * A check that looks at one Spring Boot settings file ({@code application*.properties} or {@code .yml}) at a time.
 * Like a {@link JavaCheck}, its result must depend only on that file and its options; it is cached the same way.
 */
public abstract class SettingsCheck implements Check {

    @Override
    public final List<Finding> inspect(InspectionInput input) {
        JavaModel model = JavaModel.of(input);
        RuleOptions options = input.project().config().options(id());
        List<Finding> findings = new ArrayList<>();
        for (FileChange change : input.changeSet().files()) {
            if (SettingsFile.isSettingsFile(change.path())) {
                findings.addAll(model.findings(this, change.path(), options));
            }
        }
        return List.copyOf(findings);
    }

    protected abstract List<Finding> inspect(SettingsFile file);

    protected final Finding finding(
            SettingsFile file,
            Setting setting,
            Severity severity,
            Confidence confidence,
            String whatsWrong,
            WhyItMatters whyItMatters,
            HowToFix howToFix) {
        return new Finding(
                id(),
                category(),
                severity,
                file.location(setting),
                new WhatsWrong(whatsWrong, file.snippet(setting)),
                whyItMatters,
                howToFix,
                confidence,
                FindingSource.RULE);
    }
}
