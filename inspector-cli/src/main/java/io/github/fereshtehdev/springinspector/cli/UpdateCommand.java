package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Outcome;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine.Command;

@Command(
        name = "update",
        description = "Download the latest vulnerability advisories (OSV, Maven) for offline checks."
                + " Nothing about your project is sent.")
final class UpdateCommand implements Callable<Integer> {

    private final Supplier<Outcome> update;
    private final Path database;
    private final PrintWriter out;
    private final PrintWriter err;

    UpdateCommand(Supplier<Outcome> update, Path database, PrintWriter out, PrintWriter err) {
        this.update = update;
        this.database = database;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        out.println("Checking " + AdvisoryUpdater.OSV_MAVEN + " for new advisories...");
        out.flush();
        try {
            switch (update.get()) {
                case Outcome.Updated updated ->
                    out.println("Saved " + updated.advisories() + " advisories to " + database);
                case Outcome.UpToDate upToDate ->
                    out.println("Already up to date: " + upToDate.advisories() + " advisories in " + database);
            }
            out.flush();
            return ExitCodes.OK;
        } catch (UncheckedIOException | IllegalStateException | IllegalArgumentException failure) {
            err.println("error: could not update the advisories: " + failure.getMessage());
            err.println("The advisories already on this machine, if any, are unchanged.");
            err.flush();
            return ExitCodes.FAILURE;
        }
    }
}
