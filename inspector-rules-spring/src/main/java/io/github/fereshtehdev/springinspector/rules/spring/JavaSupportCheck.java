package io.github.fereshtehdev.springinspector.rules.spring;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar.ReleaseLine;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The project targets a Java version that no longer gets free updates (as Eclipse Temurin publishes them), such as a
 * short-lived release between two LTS versions. The dates come from the release calendar that {@code update}
 * downloads; without it, this rule stays quiet.
 */
public final class JavaSupportCheck implements Check {

    static final RuleId ID = RuleId.of("migration.java-support");
    private static final List<String> HINTS = List.of(
            "<java.version>", "maven.compiler.release", "<release>", "JavaLanguageVersion", "sourceCompatibility");

    private final Clock clock;
    private final Supplier<Optional<ReleaseCalendar>> calendar;

    public JavaSupportCheck() {
        this(Clock.systemUTC(), Optional::empty);
    }

    public JavaSupportCheck(Clock clock, Supplier<Optional<ReleaseCalendar>> calendar) {
        this.clock = clock;
        this.calendar = calendar;
    }

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.MIGRATION;
    }

    @Override
    public List<Finding> inspect(InspectionInput input) {
        Optional<Version> java = input.project().versions().java();
        Optional<ReleaseCalendar> known = calendar.get();
        if (java.isEmpty() || known.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        String line = String.valueOf(java.get().major());
        Optional<LocalDate> supportEnds = known.get()
                .line(ReleaseCalendar.JAVA, line)
                .flatMap(ReleaseLine::supportEnds)
                .filter(end -> !today.isBefore(end));
        if (supportEnds.isEmpty()) {
            return List.of();
        }
        Optional<String> successor = longestSupported(known.get(), today);
        return BuildFiles.changed(input, HINTS)
                .map(build -> {
                    LineRange lines = BuildFiles.lineOf(build, HINTS);
                    return new Finding(
                            ID,
                            Category.MIGRATION,
                            Severity.INFO,
                            new Location(build.path(), lines),
                            new WhatsWrong(
                                    "Java " + line + " no longer gets free updates (they ended on " + supportEnds.get()
                                            + ")",
                                    build.snippet(lines)),
                            new WhyItMatters(
                                    "Without updates, security fixes for the JDK itself stop. Releases between two LTS"
                                            + " versions are supported for six months only.",
                                    List.of(new Reference(
                                            "Eclipse Temurin support", URI.create("https://adoptium.net/support/")))),
                            HowToFix.withoutPatch(successor
                                    .map(next -> "Move to Java " + next + ", which is supported for longest.")
                                    .orElse("Move to a supported Java LTS version.")),
                            Confidence.HIGH,
                            FindingSource.RULE);
                })
                .stream()
                .toList();
    }

    /** The supported line whose free updates last longest: in practice the newest LTS. */
    private static Optional<String> longestSupported(ReleaseCalendar calendar, LocalDate today) {
        return calendar.products().getOrDefault(ReleaseCalendar.JAVA, List.of()).stream()
                .filter(line ->
                        line.supportEnds().filter(end -> end.isAfter(today)).isPresent())
                .max(Comparator.comparing(line -> line.supportEnds().orElseThrow()))
                .map(ReleaseLine::line);
    }
}
