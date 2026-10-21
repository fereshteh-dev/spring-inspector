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
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Spring Boot minor version that is most likely past its free (open-source) support. Spring supports each minor
 * release for at least 12 months; this rule allows 13 from the first release before calling it, using release dates
 * taken from Maven Central. It is reported on the build file, and only when that file is part of the change.
 *
 * <p>The answer depends on today's date, so this is a plain {@link Check}: its result must not be cached per file.
 */
public final class SpringBootSupportCheck implements Check {

    static final RuleId ID = RuleId.of("migration.spring-boot-support");

    /** First release of each minor version, from repo1.maven.org (org.springframework.boot:spring-boot). */
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

    private static final int SUPPORT_MONTHS = 13;
    private static final Set<String> BUILD_FILES = Set.of("pom.xml", "build.gradle", "build.gradle.kts");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Once a Spring Boot line leaves open-source support, security fixes for it and for the dependency versions"
                    + " it manages are no longer published for free. Vulnerabilities found from then on stay open"
                    + " unless you upgrade or buy commercial support.",
            List.of(new Reference(
                    "Spring Boot support timeline", URI.create("https://spring.io/projects/spring-boot#support"))));

    private final Clock clock;

    public SpringBootSupportCheck() {
        this(Clock.systemUTC());
    }

    SpringBootSupportCheck(Clock clock) {
        this.clock = clock;
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
        String minor = boot.get().major() + "." + boot.get().minor();
        LocalDate released = RELEASED.get(minor);
        if (released == null || LocalDate.now(clock).isBefore(released.plusMonths(SUPPORT_MONTHS))) {
            return List.of();
        }
        return input.changeSet().files().stream()
                .map(FileChange::path)
                .filter(SpringBootSupportCheck::isBuildFile)
                .flatMap(path -> input.sources().read(path).stream())
                .filter(source -> source.content().contains(boot.get().toString())
                        || source.content().contains("spring-boot"))
                .limit(1)
                .map(source -> finding(source, boot.get(), minor, released))
                .toList();
    }

    private Finding finding(SourceFile build, Version boot, String minor, LocalDate released) {
        int line = lineOf(build.content(), boot.toString())
                .or(() -> lineOf(build.content(), "spring-boot"))
                .orElse(1);
        return new Finding(
                ID,
                Category.MIGRATION,
                Severity.INFO,
                new Location(build.path(), LineRange.line(line)),
                new WhatsWrong(
                        "Spring Boot " + minor + " (released " + released
                                + ") is most likely out of open-source support",
                        build.snippet(LineRange.line(line))),
                WHY,
                HowToFix.withoutPatch("Upgrade to a supported Spring Boot line; check the support timeline for the"
                        + " current dates. Upgrading one minor version at a time keeps each step small."),
                Confidence.MEDIUM,
                FindingSource.RULE);
    }

    private static boolean isBuildFile(Path path) {
        Path name = path.getFileName();
        return name != null && BUILD_FILES.contains(name.toString());
    }

    private static Optional<Integer> lineOf(String content, String text) {
        int index = content.indexOf(text);
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.of(
                (int) content.substring(0, index).chars().filter(c -> c == '\n').count() + 1);
    }
}
