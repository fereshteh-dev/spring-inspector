package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Outcome;
import io.github.fereshtehdev.springinspector.cli.render.Style;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class UpdateCommandTest {

    static final Path DATABASE = Path.of("home", "advisories", "osv-maven.bin");

    final StringWriter out = new StringWriter();
    final StringWriter err = new StringWriter();

    @Test
    void reportsHowManyAdvisoriesItSaved() {
        int exitCode = run(() -> new Outcome.Updated(7084));

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
        assertThat(out.toString()).contains("Saved 7084 advisories to " + DATABASE);
    }

    @Test
    void saysWhenNothingChanged() {
        int exitCode = run(() -> new Outcome.UpToDate(7084));

        assertThat(exitCode).isEqualTo(ExitCodes.OK);
        assertThat(out.toString()).contains("Already up to date: 7084 advisories");
    }

    @Test
    void explainsAFailedDownloadAndKeepsTheOldData() {
        int exitCode = run(() -> {
            throw new UncheckedIOException(
                    "Could not reach osv-vulnerabilities.storage.googleapis.com", new IOException());
        });

        assertThat(exitCode).isEqualTo(ExitCodes.FAILURE);
        assertThat(err.toString())
                .contains("could not update the advisories: Could not reach")
                .contains("unchanged");
    }

    @Test
    void treatsADamagedDownloadAsAFailureNotAUsageError() {
        int exitCode = run(() -> {
            throw new IllegalArgumentException("Not an OSV archive");
        });

        assertThat(exitCode).isEqualTo(ExitCodes.FAILURE);
    }

    @Test
    void isPartOfTheCommandLine() {
        CommandLine commandLine = SpringInspector.commandLine(
                Map.of(InspectorHome.VARIABLE, "home"),
                Path.of("user"),
                Path.of("."),
                Style.PLAIN,
                Optional.empty(),
                new PrintWriter(out),
                new PrintWriter(err));

        assertThat(commandLine.getSubcommands()).containsKey("update");
    }

    int run(Supplier<Outcome> update) {
        return new CommandLine(new UpdateCommand(update, DATABASE, new PrintWriter(out), new PrintWriter(err)))
                .execute();
    }
}
