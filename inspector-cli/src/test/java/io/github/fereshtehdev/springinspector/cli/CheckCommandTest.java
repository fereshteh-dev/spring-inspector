package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.advisories.Advisory;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase;
import io.github.fereshtehdev.springinspector.advisories.AdvisorySeverity;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryStore;
import io.github.fereshtehdev.springinspector.advisories.AffectedRange;
import io.github.fereshtehdev.springinspector.cli.CliRun.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CheckCommandTest {

    static final String INJECTABLE = "src/main/java/com/acme/Orders.java";
    static final String INJECTABLE_SOURCE = """
            package com.acme;

            import java.sql.Connection;
            import java.sql.ResultSet;
            import java.sql.SQLException;

            class Orders {
                ResultSet byId(Connection connection, String id) throws SQLException {
                    return connection.createStatement().executeQuery("SELECT * FROM orders WHERE id = " + id);
                }
            }
            """;
    static final String CLEAN = "src/main/java/com/acme/Clean.java";
    static final String CLEAN_SOURCE = """
            package com.acme;

            class Clean {
                int answer() {
                    return 42;
                }
            }
            """;

    @TempDir
    Path temp;

    CliRun cli;

    @BeforeEach
    void createRepository() {
        cli = CliRun.inNewRepository(temp);
    }

    @AfterEach
    void closeRepository() {
        cli.close();
    }

    private static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }

    @Test
    void blocksStagedChangesWithABlockerAndSaysWhere() {
        Result result = cli.write(INJECTABLE, INJECTABLE_SOURCE).stage().run("check");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(result.out())
                .contains("2 changed files - Java 25 - Spring Boot 4.1.1")
                .contains("[FAIL] Security")
                .contains("BLOCKER src/main/java/com/acme/Orders.java:9")
                .contains("Gate blocked")
                .contains("--details security");
    }

    @Test
    void passesCleanStagedChanges() {
        Result result = cli.write(CLEAN, CLEAN_SOURCE).stage().run("check", "--staged");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out()).contains("[ OK ] Security").contains("Gate passed");
    }

    @Test
    void explainsWhatToDoWhenNothingIsStaged() {
        Result result = cli.write(INJECTABLE, INJECTABLE_SOURCE).run("check");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out()).contains("0 changed files").contains("Nothing to inspect");
    }

    @Test
    void inspectsTheWholeProjectWithAll() {
        cli.write(INJECTABLE, INJECTABLE_SOURCE).commit("initial");

        assertThat(cli.run("check", "--all").exitCode()).isEqualTo(ExitCodes.BLOCKED);
    }

    @Test
    void inspectsChangesSinceABaseBranch() {
        cli.write(CLEAN, CLEAN_SOURCE).commit("initial");
        String base = cli.currentBranch();
        cli.branch("feature").write(INJECTABLE, INJECTABLE_SOURCE).commit("add orders");

        Result result = cli.run("check", "--base", base, "--format", "json");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(json(result.out()).at("/categories/0/findings/0/file").asString())
                .isEqualTo(INJECTABLE);
    }

    @Test
    void showsWhyAndHowToFixForOneCategory() {
        Result result = cli.write(INJECTABLE, INJECTABLE_SOURCE).stage().run("check", "--details", "SECURITY");

        assertThat(result.out())
                .contains("Security in detail")
                .contains("-- security.sql-concatenation - BLOCKER - confidence high")
                .contains("What's wrong")
                .contains("| ")
                .contains("Why it matters")
                .contains("-> CWE-89")
                .contains("How to fix");
    }

    @Test
    void writesJsonForScripts() {
        Result result = cli.write(INJECTABLE, INJECTABLE_SOURCE).stage().run("check", "--format", "json");
        JsonNode report = json(result.out());

        assertThat(report.at("/gate/blocks").asBoolean()).isTrue();
        assertThat(report.at("/categories/0/category").asString()).isEqualTo("security");
        assertThat(report.at("/categories/0/findings/0/ruleId").asString()).isEqualTo("security.sql-concatenation");
        assertThat(report.at("/categories/0/findings/0/startLine").asInt()).isEqualTo(9);
        assertThat(report.at("/tool/name").asString()).isEqualTo("spring-inspector");
    }

    @Test
    void writesSarifForCodeScanning() {
        Result result = cli.write(INJECTABLE, INJECTABLE_SOURCE).stage().run("check", "--format", "sarif");
        JsonNode run = json(result.out()).at("/runs/0");

        assertThat(json(result.out()).at("/version").asString()).isEqualTo("2.1.0");
        assertThat(run.at("/tool/driver/rules/0/id").asString()).isEqualTo("security.sql-concatenation");
        assertThat(run.at("/tool/driver/rules/0/properties/security-severity").asString())
                .isEqualTo("9.0");
        assertThat(run.at("/results/0/level").asString()).isEqualTo("error");
        assertThat(run.at("/results/0/locations/0/physicalLocation/artifactLocation/uri")
                        .asString())
                .isEqualTo(INJECTABLE);
        assertThat(run.at("/results/0/locations/0/physicalLocation/region/startLine")
                        .asInt())
                .isEqualTo(9);
        assertThat(run.at("/invocations/0/executionSuccessful").asBoolean()).isTrue();
    }

    @Test
    void warnsAboutFilesItCouldNotParse() {
        Result result = cli.write("src/main/java/com/acme/Broken.java", "class Broken { void m( }")
                .stage()
                .run("check", "--no-cache");

        assertThat(result.err()).contains("warning: could not parse src/main/java/com/acme/Broken.java");
    }

    @Test
    void rejectsAnUnknownCategory() {
        Result result = cli.run("check", "--details", "nope");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("Unknown category 'nope'").contains("spring-boot");
    }

    @Test
    void rejectsAnUnknownBaseRef() {
        Result result = cli.write(CLEAN, CLEAN_SOURCE).commit("initial").run("check", "--base", "nope");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("error: Unknown base ref: nope");
    }

    @Test
    void rejectsAProjectOutsideGit() {
        Result result = cli.run("check", "--project", temp.resolve("home").toString());

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("Not inside a git repository");
    }

    @Test
    void inspectsAWholeProjectOutsideGit() throws IOException {
        Path plain = temp.resolve("home").resolve("plain");
        Files.createDirectories(plain.resolve("src/main/java/com/acme"));
        Files.writeString(plain.resolve(INJECTABLE), INJECTABLE_SOURCE);

        Result result = cli.run("check", "--all", "--project", plain.toString());

        assertThat(result.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(result.out()).contains("BLOCKER src/main/java/com/acme/Orders.java:9");
    }

    @Test
    void inspectsOnlyTheNamedFilesWhetherStagedOrNot() {
        cli.write(INJECTABLE, INJECTABLE_SOURCE).write(CLEAN, CLEAN_SOURCE);

        Result clean = cli.run("check", "--files", CLEAN);
        Result injectable =
                cli.run("check", "--files", cli.project().resolve(INJECTABLE).toString(), "src/Missing.java");

        assertThat(clean.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(clean.out()).startsWith("1 changed file");
        assertThat(injectable.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(injectable.out()).contains("BLOCKER src/main/java/com/acme/Orders.java:9");
    }

    @Test
    void refusesFilesOutsideTheProject() {
        Result result = cli.run(
                "check", "--files", temp.resolve("home").resolve("Other.java").toString());

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("is not inside the project");
    }

    @Test
    void rejectsMoreThanOneScope() {
        assertThat(cli.run("check", "--all", "--staged").exitCode()).isEqualTo(ExitCodes.USAGE);
    }

    @Test
    void needsACommand() {
        Result result = cli.run();

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("check --help");
    }

    @Test
    void printsItsVersion() {
        assertThat(cli.run("--version").out()).startsWith("spring-inspector ");
    }

    @Test
    void reportsVulnerableDependenciesFromTheLocalAdvisories() {
        new AdvisoryStore(cli.home().resolve("advisories"))
                .save(new AdvisoryDatabase(
                        List.of(new Advisory(
                                "GHSA-jfh8-c2jp-5v3q",
                                List.of("CVE-2021-44228"),
                                "Remote code injection in Log4j",
                                AdvisorySeverity.CRITICAL,
                                List.of(new Advisory.AffectedPackage(
                                        "org.apache.logging.log4j:log4j-core",
                                        List.of(new AffectedRange(
                                                Optional.of("2.0"), Optional.of("2.15.0"), Optional.empty())),
                                        Set.of())))),
                        new AdvisoryDatabase.Metadata(Instant.now(), Optional.empty())));
        cli.write("pom.xml", """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>demo</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                            <version>2.14.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """).stage();

        Result result = cli.run("check");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(result.out())
                .contains("BLOCKER pom.xml:9  log4j-core 2.14.1 has a known critical vulnerability: CVE-2021-44228")
                .contains("Vulnerability advisories from ");
    }

    @Test
    void warnsWhenThereAreNoAdvisoriesToCheckDependenciesWith() {
        cli.write(CLEAN, CLEAN_SOURCE).commit("initial");

        Result result = cli.run("check", "--all");

        assertThat(result.out()).contains("No vulnerability advisories on this machine");
    }

    @Test
    void leavesAdvisoriesOutWhenTheRuleIsTurnedOff() {
        cli.write("spring-inspector.yml", """
                rules:
                  disabled: [security.vulnerable-dependency]
                """).write(CLEAN, CLEAN_SOURCE).commit("initial");

        Result result = cli.run("check", "--all");

        assertThat(result.out()).doesNotContain("advisories");
    }
}
