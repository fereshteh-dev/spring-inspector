package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Outcome;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

@Command(
        name = "update",
        description = "Download the latest vulnerability advisories (OSV, Maven) for offline checks."
                + " Nothing about your project is sent.")
final class UpdateCommand implements Callable<Integer> {

    @Option(
            names = "--max-age",
            paramLabel = "<age>",
            converter = AgeConverter.class,
            description = "Only download when the advisories are older than this, for example 12h or 1d;"
                    + " used by the git hook and the IDE.")
    @Nullable
    Duration maxAge;

    @Option(names = "--quiet", description = "Print nothing unless something goes wrong.")
    boolean quiet;

    private final Function<Optional<Duration>, Outcome> update;
    private final Path database;
    private final PrintWriter out;
    private final PrintWriter err;

    UpdateCommand(Function<Optional<Duration>, Outcome> update, Path database, PrintWriter out, PrintWriter err) {
        this.update = update;
        this.database = database;
        this.out = out;
        this.err = err;
    }

    @Override
    public Integer call() {
        if (!quiet && maxAge == null) {
            out.println("Checking " + AdvisoryUpdater.OSV_MAVEN + " for new advisories...");
        }
        try {
            String result = switch (update.apply(Optional.ofNullable(maxAge))) {
                case Outcome.Updated updated -> "Saved " + updated.advisories() + " advisories to " + database;
                case Outcome.UpToDate upToDate ->
                    "Already up to date: " + upToDate.advisories() + " advisories in " + database;
                case Outcome.Fresh fresh ->
                    "The advisories from " + LocalDate.ofInstant(fresh.updatedAt(), ZoneOffset.UTC)
                            + " are recent enough; nothing to download";
                case Outcome.RecentlyAttempted attempted ->
                    "An update was tried less than an hour ago; trying again later";
            };
            if (!quiet) {
                out.println(result);
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

    /** Ages such as {@code 30m}, {@code 12h} or {@code 1d}. */
    static final class AgeConverter implements ITypeConverter<Duration> {

        private static final Pattern AGE = Pattern.compile("(\\d{1,6})\\s*([mhd])");

        @Override
        public Duration convert(String value) {
            Matcher matcher = AGE.matcher(value.strip().toLowerCase(Locale.ROOT));
            if (!matcher.matches()) {
                throw new IllegalArgumentException("'" + value + "' is not an age such as 30m, 12h or 1d");
            }
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofDays(amount);
            };
        }
    }
}
