package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Match;
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
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.project.ProjectDependencyResolver;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.project.dependencies.ProjectDependencies;
import io.github.fereshtehdev.springinspector.project.dependencies.Unresolved;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Libraries with published vulnerabilities, matched against the local advisory database. It looks at the project's
 * dependencies when a build file is part of the change, so a commit that adds or changes a library is checked, and
 * reports one finding per library on the line that declares it.
 *
 * <p>The answer depends on the advisory data, which changes without the project changing, so this is a plain
 * {@link Check} and its results are never cached.
 */
public final class VulnerableDependencyCheck implements Check {

    public static final RuleId ID = RuleId.of("security.vulnerable-dependency");

    private static final Set<String> BUILD_FILES =
            Set.of("pom.xml", "build.gradle", "build.gradle.kts", "libs.versions.toml", "gradle.properties");
    private static final int LISTED_ADVISORIES = 5;
    private static final int LISTED_UNRESOLVED = 10;

    private final Supplier<Optional<AdvisoryDatabase>> database;
    private final Function<ProjectContext, ProjectDependencies> dependencies;

    public VulnerableDependencyCheck(AdvisoryStore store, LocalRepository repository) {
        this(once(store::load), new ProjectDependencyResolver(repository)::resolve);
    }

    VulnerableDependencyCheck(
            Supplier<Optional<AdvisoryDatabase>> database, Function<ProjectContext, ProjectDependencies> dependencies) {
        this.database = database;
        this.dependencies = dependencies;
    }

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    public List<Finding> inspect(InspectionInput input) {
        boolean buildChanged = input.changeSet().files().stream()
                .map(FileChange::path)
                .anyMatch(VulnerableDependencyCheck::isBuildFile);
        if (!buildChanged) {
            return List.of();
        }
        Optional<AdvisoryDatabase> advisories = database.get();
        if (advisories.isEmpty()) {
            return List.of();
        }
        ProjectDependencies found = dependencies.apply(input.project());
        Snippets snippets = new Snippets(input);
        List<Finding> findings = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (Dependency dependency : found.resolved()) {
            List<Match> matches = advisories.get().affecting(dependency.coordinate(), dependency.version());
            String key = dependency.coordinate() + "@" + dependency.version() + "@" + dependency.declaredIn();
            if (!matches.isEmpty() && reported.add(key)) {
                findings.add(vulnerable(dependency, matches, snippets));
            }
        }
        found.unresolved().stream()
                .collect(Collectors.groupingBy(Unresolved::declaredIn, LinkedHashMap::new, Collectors.toList()))
                .forEach((file, missing) -> findings.add(unchecked(file, missing, snippets)));
        return findings;
    }

    private static Finding vulnerable(Dependency dependency, List<Match> matches, Snippets snippets) {
        List<Advisory> advisories = matches.stream()
                .map(Match::advisory)
                .sorted(Comparator.comparingInt((Advisory advisory) -> rank(advisory.severity()))
                        .reversed())
                .toList();
        AdvisorySeverity worst = advisories.getFirst().severity();
        boolean testOnly = dependency.scope().filter("test"::equals).isPresent();
        Severity severity = testOnly ? lessSevere(severity(worst), Severity.MINOR) : severity(worst);
        String name = dependency.artifactId() + " " + dependency.version();
        String description = advisories.size() == 1
                ? name + " has a known " + label(worst) + " vulnerability: " + identifiers(advisories.getFirst())
                : name + " has " + advisories.size() + " known vulnerabilities (" + counts(advisories) + ")";
        return new Finding(
                ID,
                Category.SECURITY,
                severity,
                new Location(dependency.declaredIn(), LineRange.line(dependency.line())),
                new WhatsWrong(
                        description + (testOnly ? ", used in tests only" : ""),
                        snippets.of(dependency.declaredIn(), dependency.line())),
                why(dependency, advisories),
                HowToFix.withoutPatch(howToFix(dependency, matches)),
                dependency.origin() == Origin.PACKAGED ? Confidence.MEDIUM : Confidence.HIGH,
                FindingSource.ADVISORY);
    }

    private static WhyItMatters why(Dependency dependency, List<Advisory> advisories) {
        StringBuilder text = new StringBuilder("Attackers look for applications that ship libraries with published"
                + " vulnerabilities, because the details of how to exploit them are public.");
        advisories.stream()
                .limit(LISTED_ADVISORIES)
                .forEach(advisory -> text.append("\n- ")
                        .append(identifiers(advisory))
                        .append(" (")
                        .append(label(advisory.severity()))
                        .append("): ")
                        .append(advisory.summary()));
        if (advisories.size() > LISTED_ADVISORIES) {
            text.append("\n- and ")
                    .append(advisories.size() - LISTED_ADVISORIES)
                    .append(" more");
        }
        switch (dependency.origin()) {
            case MANAGED ->
                dependency
                        .managedBy()
                        .ifPresent(source -> text.append("\nThe version comes from ")
                                .append(source)
                                .append('.'));
            case PACKAGED ->
                dependency
                        .packagedIn()
                        .ifPresent(archive -> text.append("\nFound in ")
                                .append(archive.toString().replace('\\', '/'))
                                .append(", which this project built; rebuild it after upgrading."));
            case DECLARED -> {}
        }
        List<Reference> references = advisories.stream()
                .limit(LISTED_ADVISORIES)
                .map(advisory ->
                        new Reference(advisory.id(), URI.create("https://osv.dev/vulnerability/" + advisory.id())))
                .toList();
        return new WhyItMatters(text.toString(), references);
    }

    private static String howToFix(Dependency dependency, List<Match> matches) {
        Optional<String> safe = safeVersion(dependency.version(), matches);
        if (safe.isEmpty()) {
            return "No release of " + dependency.artifactId() + " fixes all of these yet. Read the advisories for"
                    + " workarounds, limit how the library is exposed, or replace it.";
        }
        String target = dependency.artifactId() + " " + safe.get() + " or later";
        return switch (dependency.origin()) {
            case DECLARED -> "Upgrade to " + target + ".";
            case MANAGED ->
                "Upgrade " + dependency.managedBy().orElse("the BOM or parent that manages it")
                        + " to a release that brings " + target
                        + ", or set the version yourself (Maven: dependencyManagement; Gradle: a constraint).";
            case PACKAGED ->
                dependency.line() > 1
                        ? "Upgrade to " + target + "."
                        : dependency.artifactId() + " comes in through another library. Upgrade the library that"
                                + " brings it, or require " + target
                                + " (Maven: dependencyManagement; Gradle: a constraint).";
        };
    }

    /** The lowest published fix above the current version that none of the matching advisories affects. */
    static Optional<String> safeVersion(String current, List<Match> matches) {
        MavenVersion installed = MavenVersion.parse(current);
        return matches.stream()
                .flatMap(match -> match.affected().ranges().stream())
                .flatMap(range -> range.fixed().stream())
                .distinct()
                .map(MavenVersion::parse)
                .filter(candidate -> candidate.compareTo(installed) > 0)
                .sorted()
                .filter(candidate ->
                        matches.stream().noneMatch(match -> match.affected().affects(candidate.toString())))
                .map(MavenVersion::toString)
                .findFirst();
    }

    private static Finding unchecked(Path file, List<Unresolved> missing, Snippets snippets) {
        Unresolved first = missing.getFirst();
        String names = missing.stream()
                .limit(LISTED_UNRESOLVED)
                .map(Unresolved::coordinate)
                .collect(Collectors.joining(", "));
        String more =
                missing.size() > LISTED_UNRESOLVED ? " and " + (missing.size() - LISTED_UNRESOLVED) + " more" : "";
        return new Finding(
                ID,
                Category.SECURITY,
                Severity.INFO,
                new Location(file, LineRange.line(first.line())),
                new WhatsWrong(
                        missing.size() + (missing.size() == 1 ? " library was" : " libraries were")
                                + " not checked for vulnerabilities: " + names + more,
                        snippets.of(file, first.line())),
                new WhyItMatters(
                        "Their versions could not be worked out without running the build; for example, "
                                + first.coordinate() + ": " + first.reason() + ".",
                        List.of()),
                HowToFix.withoutPatch("Build the project once (so its BOMs and parents are in the local cache, or its"
                        + " application archive exists), or write the versions in the build file."),
                Confidence.HIGH,
                FindingSource.ADVISORY);
    }

    private static String identifiers(Advisory advisory) {
        return String.join(", ", advisory.identifiers());
    }

    private static String counts(List<Advisory> advisories) {
        Map<AdvisorySeverity, Long> counts = advisories.stream()
                .collect(Collectors.groupingBy(Advisory::severity, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> entry.getValue() + " " + label(entry.getKey()))
                .collect(Collectors.joining(", "));
    }

    private static String label(AdvisorySeverity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case HIGH -> "high";
            case MODERATE -> "moderate";
            case LOW -> "low";
            case UNKNOWN -> "unrated";
        };
    }

    static Severity severity(AdvisorySeverity severity) {
        return switch (severity) {
            case CRITICAL -> Severity.BLOCKER;
            case HIGH -> Severity.MAJOR;
            case MODERATE, UNKNOWN -> Severity.MINOR;
            case LOW -> Severity.INFO;
        };
    }

    private static Severity lessSevere(Severity a, Severity b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static boolean isBuildFile(Path path) {
        Path name = path.getFileName();
        return name != null && BUILD_FILES.contains(name.toString());
    }

    /** Loads the database on first use, from whichever check thread gets there first. */
    private static Supplier<Optional<AdvisoryDatabase>> once(Supplier<Optional<AdvisoryDatabase>> load) {
        return new Supplier<>() {
            private Optional<AdvisoryDatabase> loaded = Optional.empty();
            private boolean done;

            @Override
            public synchronized Optional<AdvisoryDatabase> get() {
                if (!done) {
                    loaded = load.get();
                    done = true;
                }
                return loaded;
            }
        };
    }

    /** Worse is higher; {@link AdvisorySeverity#UNKNOWN} is declared last but ranks lowest. */
    private static int rank(AdvisorySeverity severity) {
        return switch (severity) {
            case UNKNOWN -> 0;
            case LOW -> 1;
            case MODERATE -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }

    private static final class Snippets {

        private final InspectionInput input;

        Snippets(InspectionInput input) {
            this.input = input;
        }

        String of(Path file, int line) {
            return input.sources()
                    .read(file)
                    .map(source -> source.snippet(LineRange.line(line)))
                    .orElse("");
        }
    }
}
