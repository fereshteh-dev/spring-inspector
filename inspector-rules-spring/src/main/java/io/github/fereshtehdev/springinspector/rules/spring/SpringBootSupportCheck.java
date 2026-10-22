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
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A Spring Boot line that is out of open-source support, or leaves it within three months. The dates come from the
 * release calendar that {@code update} downloads; without it, the rule estimates them from release dates (Spring
 * supports each line for at least 12 months; 13 are allowed) and says it is an estimate. It is reported on the build
 * file, and only when that file is part of the change.
 *
 * <p>The answer depends on today's date, so this is a plain {@link Check}: its result must not be cached per file.
 */
public final class SpringBootSupportCheck implements Check {

    static final RuleId ID = RuleId.of("migration.spring-boot-support");

    /** First release of each line, used only when no release calendar has been downloaded. */
    static final Map<String, LocalDate> RELEASED = Map.of(
            "2.7", LocalDate.of(2022, 5, 19),
            "3.0", LocalDate.of(2022, 11, 24),
            "3.1", LocalDate.of(2023, 5, 18),
            "3.2", LocalDate.of(2023, 11, 23),
            "3.3", LocalDate.of(2024, 5, 23),
            "3.4", LocalDate.of(2024, 11, 21),
            "3.5", LocalDate.of(2025, 5, 22),
            "4.0", LocalDate.of(2025, 11, 20),
            "4.1", LocalDate.of(2026, 6, 10));

    static final int WARNING_DAYS = 90;
    private static final int SUPPORT_MONTHS = 13;

    private static final WhyItMatters WHY = new WhyItMatters(
            "Once a Spring Boot line leaves open-source support, security fixes for it and for the dependency versions"
                    + " it manages are no longer published for free. Vulnerabilities found from then on stay open"
                    + " unless you upgrade or buy commercial support.",
            List.of(new Reference(
                    "Spring Boot support timeline", URI.create("https://spring.io/projects/spring-boot#support"))));

    private final Clock clock;
    private final Supplier<Optional<ReleaseCalendar>> calendar;

    public SpringBootSupportCheck() {
        this(Clock.systemUTC(), Optional::empty);
    }

    SpringBootSupportCheck(Clock clock) {
        this(clock, Optional::empty);
    }

    public SpringBootSupportCheck(Clock clock, Supplier<Optional<ReleaseCalendar>> calendar) {
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
        Optional<Version> boot = input.project().versions().springBoot();
        if (boot.isEmpty()) {
            return List.of();
        }
        List<String> hints = List.of(boot.get().toString(), "spring-boot");
        return BuildFiles.changed(input, hints)
                .flatMap(build ->
                        verdict(boot.get()).map(verdict -> finding(build, BuildFiles.lineOf(build, hints), verdict)))
                .stream()
                .toList();
    }

    private Optional<Verdict> verdict(Version boot) {
        String line = boot.major() + "." + boot.minor();
        LocalDate today = LocalDate.now(clock);
        Optional<LocalDate> supportEnds = calendar.get()
                .flatMap(known -> known.line(ReleaseCalendar.SPRING_BOOT, line))
                .flatMap(ReleaseCalendar.ReleaseLine::supportEnds);
        if (supportEnds.isPresent()) {
            LocalDate end = supportEnds.get();
            if (!today.isBefore(end)) {
                return Optional.of(
                        new Verdict("Spring Boot " + line + " left open-source support on " + end, Confidence.HIGH));
            }
            long days = ChronoUnit.DAYS.between(today, end);
            return days <= WARNING_DAYS
                    ? Optional.of(new Verdict(
                            "Spring Boot " + line + " leaves open-source support on " + end + ", in " + days + " days",
                            Confidence.HIGH))
                    : Optional.empty();
        }
        LocalDate released = RELEASED.get(line);
        if (released == null || today.isBefore(released.plusMonths(SUPPORT_MONTHS))) {
            return Optional.empty();
        }
        return Optional.of(new Verdict(
                "Spring Boot " + line + " (released " + released + ") is most likely out of open-source support",
                Confidence.MEDIUM));
    }

    private static Finding finding(SourceFile build, LineRange line, Verdict verdict) {
        return new Finding(
                ID,
                Category.MIGRATION,
                Severity.INFO,
                new Location(build.path(), line),
                new WhatsWrong(verdict.description(), build.snippet(line)),
                WHY,
                HowToFix.withoutPatch("Upgrade to a supported Spring Boot line; check the support timeline for the"
                        + " current dates. Upgrading one minor version at a time keeps each step small."),
                verdict.confidence(),
                FindingSource.RULE);
    }

    private record Verdict(String description, Confidence confidence) {}
}
