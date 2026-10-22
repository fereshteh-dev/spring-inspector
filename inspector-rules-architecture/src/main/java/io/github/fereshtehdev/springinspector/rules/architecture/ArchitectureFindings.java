package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit.Import;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;

/** What the architecture rules share: when they run, and how a finding on an import statement looks. */
final class ArchitectureFindings {

    private ArchitectureFindings() {}

    /** The rules map the whole project, so they run only when Java code is part of the change. */
    static boolean touchesJava(InspectionInput input) {
        return input.changeSet().files().stream()
                .anyMatch(file -> file.path().toString().endsWith(".java"));
    }

    static Finding onImport(
            RuleId rule,
            Severity severity,
            Confidence confidence,
            JavaUnit unit,
            Import statement,
            String description,
            WhyItMatters why,
            String howToFix) {
        return new Finding(
                rule,
                Category.ARCHITECTURE,
                severity,
                new Location(unit.file(), LineRange.line(statement.line())),
                new WhatsWrong(description, "import " + statement.name() + (statement.wildcard() ? ".*" : "") + ";"),
                why,
                HowToFix.withoutPatch(howToFix),
                confidence,
                FindingSource.RULE);
    }
}
