package io.github.fereshtehdev.springinspector.javamodel.cache;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

final class SampleFindings {

    static final Path FILE = Path.of("src", "main", "java", "App.java");

    private SampleFindings() {}

    static Finding withPatch() {
        return new Finding(
                RuleId.of("security.sample"),
                Category.SECURITY,
                Severity.BLOCKER,
                new Location(FILE, LineRange.of(3, 4)),
                new WhatsWrong("Something is wrong", "  query(\"x\" + y);\n  ünïcode"),
                new WhyItMatters(
                        "Because", List.of(new Reference("CWE-89", URI.create("https://cwe.mitre.org/data/89")))),
                HowToFix.withPatch("Bind it", new Patch("--- a/x\n+++ b/x\n")),
                Confidence.MEDIUM,
                FindingSource.RULE);
    }

    static Finding withoutPatch() {
        return new Finding(
                RuleId.of("cleancode.sample"),
                Category.CLEAN_CODE,
                Severity.MINOR,
                new Location(FILE, LineRange.line(9)),
                new WhatsWrong("Long", ""),
                new WhyItMatters("Hard to read", List.of()),
                HowToFix.withoutPatch("Split it"),
                Confidence.HIGH,
                FindingSource.RULE);
    }
}
