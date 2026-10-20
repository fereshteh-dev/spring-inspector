package io.github.fereshtehdev.springinspector.core.domain;

public record Finding(
        RuleId ruleId,
        Category category,
        Severity severity,
        Location location,
        WhatsWrong whatsWrong,
        WhyItMatters whyItMatters,
        HowToFix howToFix,
        Confidence confidence,
        FindingSource source) {

    public Finding {
        if (source == FindingSource.AI && severity == Severity.BLOCKER) {
            throw new IllegalArgumentException("AI findings are never BLOCKER: " + ruleId);
        }
    }

    public Finding withSeverity(Severity newSeverity) {
        return new Finding(
                ruleId, category, newSeverity, location, whatsWrong, whyItMatters, howToFix, confidence, source);
    }
}
