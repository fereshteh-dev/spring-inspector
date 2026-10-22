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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A Spring Boot or Spring AI version with a newer patch release in the same line. Patch releases fix bugs and security
 * issues without breaking changes, so staying on the newest one is cheap. The newest releases come from the release
 * calendar that {@code update} downloads; without it, this rule has nothing to compare with and stays quiet.
 */
public final class NewerPatchCheck implements Check {

    static final RuleId ID = RuleId.of("migration.newer-patch");
    private static final Map<String, String> PROJECT_PAGES = Map.of(
            "Spring Boot", "https://spring.io/projects/spring-boot#learn",
            "Spring AI", "https://spring.io/projects/spring-ai#learn");

    private final Supplier<Optional<ReleaseCalendar>> calendar;

    public NewerPatchCheck() {
        this(Optional::empty);
    }

    public NewerPatchCheck(Supplier<Optional<ReleaseCalendar>> calendar) {
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
        Optional<ReleaseCalendar> known = calendar.get();
        if (known.isEmpty()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        input.project()
                .versions()
                .springBoot()
                .ifPresent(version -> newer(
                                input, known.get(), ReleaseCalendar.SPRING_BOOT, "Spring Boot", version, "spring-boot")
                        .ifPresent(findings::add));
        input.project()
                .versions()
                .springAi()
                .ifPresent(version -> newer(
                                input, known.get(), ReleaseCalendar.SPRING_AI, "Spring AI", version, "spring-ai")
                        .ifPresent(findings::add));
        return findings;
    }

    private static Optional<Finding> newer(
            InspectionInput input,
            ReleaseCalendar calendar,
            String product,
            String name,
            Version used,
            String artifactHint) {
        String line = used.major() + "." + used.minor();
        Optional<String> latest = calendar.line(product, line).flatMap(ReleaseCalendar.ReleaseLine::latest);
        if (latest.isEmpty() || !isNewer(latest.get(), used)) {
            return Optional.empty();
        }
        List<String> hints = List.of(used.toString(), artifactHint);
        return BuildFiles.changed(input, hints)
                .map(build -> finding(build, BuildFiles.lineOf(build, hints), name, used, line, latest.get()));
    }

    private static boolean isNewer(String latest, Version used) {
        try {
            return Version.parse(latest).compareTo(used) > 0;
        } catch (IllegalArgumentException notAVersion) {
            return false;
        }
    }

    private static Finding finding(
            SourceFile build, LineRange lines, String name, Version used, String line, String latest) {
        return new Finding(
                ID,
                Category.MIGRATION,
                Severity.INFO,
                new Location(build.path(), lines),
                new WhatsWrong(
                        name + " " + used + " is used; " + latest + " is the newest " + line + " release",
                        build.snippet(lines)),
                new WhyItMatters(
                        "Patch releases fix bugs and security problems and do not change APIs, so upgrading within a"
                                + " line is cheap, and staying behind keeps known problems in the application.",
                        List.of(new Reference(
                                name + " releases",
                                URI.create(PROJECT_PAGES.getOrDefault(name, "https://spring.io/projects"))))),
                HowToFix.withoutPatch("Change the version to " + latest + " and run the tests."),
                Confidence.HIGH,
                FindingSource.RULE);
    }
}
