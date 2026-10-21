package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.advisories.Advisory.AffectedPackage;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Metadata;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency;
import io.github.fereshtehdev.springinspector.project.dependencies.Dependency.Origin;
import io.github.fereshtehdev.springinspector.project.dependencies.LocalRepository;
import io.github.fereshtehdev.springinspector.project.dependencies.ProjectDependencies;
import io.github.fereshtehdev.springinspector.project.dependencies.Unresolved;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VulnerableDependencyCheckTest {

    static final String POM = """
            <project>
                <dependencies>
                    <dependency>
                        <artifactId>log4j-core</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    static final Advisory LOG4SHELL = advisory(
            "GHSA-jfh8-c2jp-5v3q",
            "CVE-2021-44228",
            AdvisorySeverity.CRITICAL,
            "Remote code injection in Log4j",
            AdvisoryTest.range("2.0", "2.15.0", null));
    static final Advisory FOLLOW_UP = advisory(
            "GHSA-7rjr-3q55-vv33",
            "CVE-2021-45046",
            AdvisorySeverity.CRITICAL,
            "Incomplete fix for Apache Log4j vulnerability",
            AdvisoryTest.range("2.0", "2.16.0", null));
    static final Advisory RECURSION = advisory(
            "GHSA-p6xc-xr62-6r2g",
            "CVE-2021-45105",
            AdvisorySeverity.HIGH,
            "Uncontrolled recursion",
            AdvisoryTest.range("2.0", "2.17.0", null));

    @TempDir
    Path home;

    @Test
    void reportsAVulnerableLibraryOnItsLineWithTheLowestSafeVersion() {
        List<Finding> findings = check(List.of(LOG4SHELL, FOLLOW_UP, RECURSION), declared("2.14.1", Origin.DECLARED))
                .inspect(input("pom.xml"));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.ruleId()).isEqualTo(VulnerableDependencyCheck.ID);
            assertThat(finding.category()).isEqualTo(Category.SECURITY);
            assertThat(finding.severity()).isEqualTo(Severity.BLOCKER);
            assertThat(finding.source()).isEqualTo(FindingSource.ADVISORY);
            assertThat(finding.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(finding.location().file()).isEqualTo(Path.of("pom.xml"));
            assertThat(finding.location().lines()).isEqualTo(LineRange.line(4));
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("log4j-core 2.14.1 has 3 known vulnerabilities (2 critical, 1 high)");
            assertThat(finding.whatsWrong().snippet()).contains("log4j-core");
            assertThat(finding.whyItMatters().explanation())
                    .contains("- CVE-2021-44228 (critical): Remote code injection in Log4j")
                    .contains("- CVE-2021-45105 (high): Uncontrolled recursion");
            assertThat(finding.whyItMatters().references())
                    .extracting(reference -> reference.uri().toString())
                    .contains("https://osv.dev/vulnerability/GHSA-jfh8-c2jp-5v3q");
            assertThat(finding.howToFix().explanation()).isEqualTo("Upgrade to log4j-core 2.17.0 or later.");
        });
    }

    @Test
    void namesASingleAdvisoryByItsCve() {
        List<Finding> findings =
                check(List.of(RECURSION), declared("2.16.0", Origin.DECLARED)).inspect(input("pom.xml"));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.MAJOR);
            assertThat(finding.whatsWrong().description())
                    .isEqualTo("log4j-core 2.16.0 has a known high vulnerability: CVE-2021-45105");
        });
    }

    @Test
    void saysWhereAManagedVersionComesFromAndWhatToUpgrade() {
        Dependency managed = new Dependency(
                "org.apache.logging.log4j",
                "log4j-core",
                "2.14.1",
                Origin.MANAGED,
                Path.of("pom.xml"),
                4,
                Optional.empty(),
                Optional.of("spring-boot-starter-parent 2.5.0"),
                Optional.empty());

        Finding finding =
                check(List.of(LOG4SHELL), managed).inspect(input("pom.xml")).getFirst();

        assertThat(finding.whyItMatters().explanation())
                .contains("The version comes from spring-boot-starter-parent 2.5.0.");
        assertThat(finding.howToFix().explanation())
                .startsWith("Upgrade spring-boot-starter-parent 2.5.0 to a release that brings log4j-core 2.15.0");
    }

    @Test
    void explainsLibrariesThatOnlyTheBuiltArchiveContains() {
        Dependency transitive = new Dependency(
                "org.apache.logging.log4j",
                "log4j-core",
                "2.14.1",
                Origin.PACKAGED,
                Path.of("pom.xml"),
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.of(Path.of("target", "app.jar")));
        Dependency declaredButPackaged = new Dependency(
                "org.apache.logging.log4j",
                "log4j-core",
                "2.14.1",
                Origin.PACKAGED,
                Path.of("app", "pom.xml"),
                4,
                Optional.empty(),
                Optional.empty(),
                Optional.of(Path.of("app", "target", "app.jar")));

        List<Finding> findings =
                check(List.of(LOG4SHELL), transitive, declaredButPackaged).inspect(input("pom.xml"));

        assertThat(findings)
                .hasSize(2)
                .allSatisfy(finding -> assertThat(finding.confidence()).isEqualTo(Confidence.MEDIUM));
        assertThat(findings.get(0).whyItMatters().explanation()).contains("Found in target/app.jar");
        assertThat(findings.get(0).howToFix().explanation()).startsWith("log4j-core comes in through another library");
        assertThat(findings.get(1).howToFix().explanation()).isEqualTo("Upgrade to log4j-core 2.15.0 or later.");
    }

    @Test
    void lowersTestOnlyLibrariesAndSaysSo() {
        Dependency test = new Dependency(
                "org.apache.logging.log4j",
                "log4j-core",
                "2.14.1",
                Origin.DECLARED,
                Path.of("pom.xml"),
                4,
                Optional.of("test"),
                Optional.empty(),
                Optional.empty());

        Finding finding =
                check(List.of(LOG4SHELL), test).inspect(input("pom.xml")).getFirst();

        assertThat(finding.severity()).isEqualTo(Severity.MINOR);
        assertThat(finding.whatsWrong().description()).endsWith(", used in tests only");
    }

    @Test
    void saysWhenNoReleaseFixesEverything() {
        Advisory unfixed = advisory(
                "GHSA-open", "CVE-2099-1", AdvisorySeverity.LOW, "Not fixed", AdvisoryTest.range("2.0", null, null));

        Finding finding = check(List.of(unfixed, LOG4SHELL), declared("2.14.1", Origin.DECLARED))
                .inspect(input("pom.xml"))
                .getFirst();

        assertThat(finding.howToFix().explanation()).startsWith("No release of log4j-core fixes all of these yet");
        assertThat(finding.whatsWrong().description()).contains("(1 critical, 1 low)");
    }

    @Test
    void skipsALinePublishedOnlyAsLastAffectedForTheNextFixedRelease() {
        Advisory commercialOnly = new Advisory(
                "GHSA-3chg",
                List.of("CVE-2026-41845"),
                "Spring MVC issue",
                AdvisorySeverity.HIGH,
                List.of(new AffectedPackage(
                        "org.apache.logging.log4j:log4j-core",
                        List.of(
                                AdvisoryTest.range("2.0", null, "2.14.9"),
                                AdvisoryTest.range("2.15.0", "2.15.3", null),
                                AdvisoryTest.range("2.16.0", "2.16.1", null)),
                        Set.of())));

        Finding finding = check(List.of(commercialOnly), declared("2.14.1", Origin.DECLARED))
                .inspect(input("pom.xml"))
                .getFirst();

        assertThat(finding.howToFix().explanation()).isEqualTo("Upgrade to log4j-core 2.15.3 or later.");
    }

    @Test
    void listsLibrariesItCouldNotCheckOncePerBuildFile() {
        List<Unresolved> unresolved = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new Unresolved("com.example:lib" + i, Path.of("pom.xml"), 4, "no version"))
                .toList();
        VulnerableDependencyCheck check = new VulnerableDependencyCheck(
                () -> Optional.of(database(List.of())), project -> new ProjectDependencies(List.of(), unresolved));

        assertThat(check.inspect(input("pom.xml"))).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.INFO);
            assertThat(finding.whatsWrong().description())
                    .startsWith("12 libraries were not checked for vulnerabilities: com.example:lib1, ")
                    .endsWith("com.example:lib10 and 2 more");
            assertThat(finding.whyItMatters().explanation()).contains("com.example:lib1: no version");
        });
    }

    @Test
    void staysQuietWhenNoBuildFileChangedOrThereAreNoAdvisories() {
        VulnerableDependencyCheck withAdvisories = check(List.of(LOG4SHELL), declared("2.14.1", Origin.DECLARED));
        VulnerableDependencyCheck withoutAdvisories = new VulnerableDependencyCheck(
                Optional::empty,
                project -> new ProjectDependencies(List.of(declared("2.14.1", Origin.DECLARED)), List.of()));

        assertThat(withAdvisories.inspect(input("src/main/java/App.java"))).isEmpty();
        assertThat(withoutAdvisories.inspect(input("pom.xml"))).isEmpty();
        assertThat(check(List.of(LOG4SHELL), declared("2.17.1", Origin.DECLARED))
                        .inspect(input("pom.xml")))
                .isEmpty();
    }

    @Test
    void readsTheStoreOnlyOnceAndOnlyWhenNeeded() throws Exception {
        AdvisoryStore store = new AdvisoryStore(home);
        store.save(database(List.of(LOG4SHELL)));
        VulnerableDependencyCheck check = new VulnerableDependencyCheck(store, LocalRepository.none());
        Path project = Files.createDirectories(home.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                    <groupId>demo</groupId><artifactId>demo</artifactId><version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <version>2.14.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        InspectionInput input = new InspectionInput(
                new ProjectContext(
                        project, DetectedVersions.none(), BuildTool.MAVEN, List.of(), InspectorConfig.defaults()),
                ChangeSet.of(FileChange.entireFile("pom.xml")),
                path -> Optional.empty());

        assertThat(check.inspect(input)).hasSize(1);
        Files.delete(store.file());
        assertThat(check.inspect(input)).hasSize(1);
        assertThat(check.id()).isEqualTo(VulnerableDependencyCheck.ID);
        assertThat(check.category()).isEqualTo(Category.SECURITY);
    }

    @Test
    void mapsAdvisorySeveritiesToFindingSeverities() {
        assertThat(VulnerableDependencyCheck.severity(AdvisorySeverity.CRITICAL))
                .isEqualTo(Severity.BLOCKER);
        assertThat(VulnerableDependencyCheck.severity(AdvisorySeverity.HIGH)).isEqualTo(Severity.MAJOR);
        assertThat(VulnerableDependencyCheck.severity(AdvisorySeverity.MODERATE))
                .isEqualTo(Severity.MINOR);
        assertThat(VulnerableDependencyCheck.severity(AdvisorySeverity.UNKNOWN)).isEqualTo(Severity.MINOR);
        assertThat(VulnerableDependencyCheck.severity(AdvisorySeverity.LOW)).isEqualTo(Severity.INFO);
    }

    static VulnerableDependencyCheck check(List<Advisory> advisories, Dependency... dependencies) {
        return new VulnerableDependencyCheck(
                () -> Optional.of(database(advisories)),
                project -> new ProjectDependencies(List.of(dependencies), List.of()));
    }

    static Dependency declared(String version, Origin origin) {
        return new Dependency(
                "org.apache.logging.log4j",
                "log4j-core",
                version,
                origin,
                Path.of("pom.xml"),
                4,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    static InspectionInput input(String changed) {
        return new InspectionInput(
                new ProjectContext(
                        Path.of("project"),
                        DetectedVersions.none(),
                        BuildTool.MAVEN,
                        List.of(),
                        InspectorConfig.defaults()),
                ChangeSet.of(FileChange.entireFile(changed)),
                path -> path.equals(Path.of("pom.xml")) ? Optional.of(new SourceFile(path, POM)) : Optional.empty());
    }

    static AdvisoryDatabase database(List<Advisory> advisories) {
        return new AdvisoryDatabase(advisories, new Metadata(Instant.EPOCH, Optional.empty()));
    }

    static Advisory advisory(String id, String cve, AdvisorySeverity severity, String summary, AffectedRange range) {
        return new Advisory(
                id,
                List.of(cve),
                summary,
                severity,
                List.of(new AffectedPackage("org.apache.logging.log4j:log4j-core", List.of(range), Set.of())));
    }
}
