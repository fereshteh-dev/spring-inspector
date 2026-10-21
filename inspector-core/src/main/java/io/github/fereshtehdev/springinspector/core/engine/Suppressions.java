package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.report.SuppressedFinding;
import io.github.fereshtehdev.springinspector.core.port.SourceProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Honours {@code // inspector:ignore <ruleId> reason="..."} on a finding's first line or the line above it. A
 * suppression without a reason is not honoured and is reported instead, so silence always comes with a why.
 */
final class Suppressions {

    private static final Pattern COMMENT =
            Pattern.compile("//\\s*inspector:ignore\\s+([A-Za-z0-9][A-Za-z0-9._-]*)([^\\r\\n]*)");
    private static final Pattern REASON = Pattern.compile("reason\\s*=\\s*\"([^\"]*)\"");

    record Outcome(List<Finding> kept, List<SuppressedFinding> suppressed, List<Location> withoutReason) {}

    private Suppressions() {}

    static Outcome apply(List<Finding> findings, SourceProvider sources) {
        Map<Path, List<String>> lines = new HashMap<>();
        List<Finding> kept = new ArrayList<>();
        List<SuppressedFinding> suppressed = new ArrayList<>();
        Set<Location> withoutReason = new LinkedHashSet<>();
        for (Finding finding : findings) {
            Path file = finding.location().file();
            List<String> fileLines = lines.computeIfAbsent(file, path -> linesOf(sources, path));
            Optional<String> reason = Optional.empty();
            int first = finding.location().lines().start();
            for (int line = first; line >= Math.max(1, first - 1) && reason.isEmpty(); line--) {
                Optional<Suppression> suppression = suppressionAt(fileLines, line, finding);
                if (suppression.isPresent()) {
                    reason = suppression.get().reason();
                    if (reason.isEmpty()) {
                        withoutReason.add(new Location(file, LineRange.line(line)));
                    }
                }
            }
            reason.ifPresentOrElse(
                    because -> suppressed.add(new SuppressedFinding(finding, because)), () -> kept.add(finding));
        }
        return new Outcome(kept, suppressed, List.copyOf(withoutReason));
    }

    private static Optional<Suppression> suppressionAt(List<String> lines, int line, Finding finding) {
        if (line > lines.size()) {
            return Optional.empty();
        }
        Matcher comment = COMMENT.matcher(lines.get(line - 1));
        while (comment.find()) {
            if (comment.group(1).equals(finding.ruleId().value())) {
                Matcher reason = REASON.matcher(comment.group(2));
                boolean justified = reason.find() && !reason.group(1).isBlank();
                return Optional.of(
                        new Suppression(justified ? Optional.of(reason.group(1).strip()) : Optional.empty()));
            }
        }
        return Optional.empty();
    }

    private static List<String> linesOf(SourceProvider sources, Path file) {
        return sources.read(file)
                .map(SourceFile::content)
                .map(content -> content.lines().toList())
                .orElse(List.of());
    }

    private record Suppression(Optional<String> reason) {}
}
