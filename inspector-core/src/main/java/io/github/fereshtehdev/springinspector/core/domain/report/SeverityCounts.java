package io.github.fereshtehdev.springinspector.core.domain.report;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import java.util.Collection;

public record SeverityCounts(int blocker, int major, int minor, int info) {

    public static SeverityCounts of(Collection<Finding> findings) {
        return new SeverityCounts(
                count(findings, Severity.BLOCKER),
                count(findings, Severity.MAJOR),
                count(findings, Severity.MINOR),
                count(findings, Severity.INFO));
    }

    private static int count(Collection<Finding> findings, Severity severity) {
        return (int) findings.stream().filter(f -> f.severity() == severity).count();
    }

    public int total() {
        return blocker + major + minor + info;
    }

    public int issues() {
        return blocker + major;
    }
}
