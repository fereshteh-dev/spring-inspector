package io.github.fereshtehdev.springinspector.core.testing;

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
import java.util.List;

public final class FindingBuilder {

    private String ruleId = "TEST-001";
    private Category category = Category.SECURITY;
    private Severity severity = Severity.MAJOR;
    private Location location = Location.of("src/main/java/App.java", LineRange.line(1));
    private Confidence confidence = Confidence.HIGH;
    private FindingSource source = FindingSource.RULE;

    private FindingBuilder() {}

    public static FindingBuilder aFinding() {
        return new FindingBuilder();
    }

    public FindingBuilder rule(String ruleId) {
        this.ruleId = ruleId;
        return this;
    }

    public FindingBuilder category(Category category) {
        this.category = category;
        return this;
    }

    public FindingBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    public FindingBuilder at(String file, int line) {
        this.location = Location.of(file, LineRange.line(line));
        return this;
    }

    public FindingBuilder confidence(Confidence confidence) {
        this.confidence = confidence;
        return this;
    }

    public FindingBuilder source(FindingSource source) {
        this.source = source;
        return this;
    }

    public Finding build() {
        return new Finding(
                RuleId.of(ruleId),
                category,
                severity,
                location,
                new WhatsWrong("Something is wrong", "code();"),
                new WhyItMatters("It matters", List.of()),
                HowToFix.withoutPatch("Fix it"),
                confidence,
                source);
    }
}
