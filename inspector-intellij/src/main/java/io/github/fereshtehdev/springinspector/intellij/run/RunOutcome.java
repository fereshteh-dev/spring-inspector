package io.github.fereshtehdev.springinspector.intellij.run;

import io.github.fereshtehdev.springinspector.intellij.report.Report;
import io.github.fereshtehdev.springinspector.intellij.report.ReportReader;
import java.util.List;

/** How a run ended: with a report, or with a message saying why there is none. */
public sealed interface RunOutcome {

    /** The command line's exit codes: 0 passed, 1 blocked; both come with a report. */
    int PASSED = 0;

    int BLOCKED = 1;

    /**
     * A run that produced a report.
     *
     * @param warnings what the command line printed to standard error alongside the report
     */
    record Completed(Report report, List<String> warnings) implements RunOutcome {

        public Completed {
            warnings = List.copyOf(warnings);
        }
    }

    record Failed(String message) implements RunOutcome {}

    /** Interprets the finished process; the inspection's own output is data and only ever parsed. */
    static RunOutcome of(int exitCode, String stdout, String stderr) {
        List<String> messages = stderr.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        if (exitCode != PASSED && exitCode != BLOCKED) {
            String detail = messages.isEmpty() ? "exit code " + exitCode : String.join("\n", messages);
            return new Failed("Spring Inspector could not finish: " + detail);
        }
        try {
            List<String> warnings = messages.stream()
                    .map(line -> line.startsWith("warning: ") ? line.substring("warning: ".length()) : line)
                    .toList();
            return new Completed(ReportReader.read(stdout), warnings);
        } catch (IllegalArgumentException unreadable) {
            return new Failed("Spring Inspector's report could not be read: " + unreadable.getMessage());
        }
    }
}
