package io.github.fereshtehdev.springinspector.cli;

import static io.github.fereshtehdev.springinspector.cli.CheckCommandTest.INJECTABLE;
import static io.github.fereshtehdev.springinspector.cli.CheckCommandTest.INJECTABLE_SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.cli.CliRun.Result;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.project.config.ConfigFileReader;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ConfigurationTest {

    private static final String SUPPRESSED_SOURCE = INJECTABLE_SOURCE.replace(
            "        return connection",
            "        // inspector:ignore security.sql-concatenation reason=\"id is a validated UUID\"\n"
                    + "        return connection");

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

    private Result checkWithSettings(String yaml, String... arguments) {
        cli.write(ConfigFileReader.FILE_NAME, yaml).stage();
        String[] check = new String[arguments.length + 1];
        check[0] = "check";
        System.arraycopy(arguments, 0, check, 1, arguments.length);
        return cli.run(check);
    }

    private static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }

    @Test
    void initWritesTheDefaultSettingsOnce() {
        Result first = cli.run("init");
        Result second = cli.run("init");

        assertThat(first.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(first.out()).contains("Wrote ").contains(ConfigFileReader.FILE_NAME);
        InspectorConfig written = new ConfigFileReader().read(cli.project()).config();
        InspectorConfig defaults = InspectorConfig.defaults();
        assertThat(written.gatePolicy()).isEqualTo(defaults.gatePolicy());
        assertThat(written.checkTimeout()).isEqualTo(defaults.checkTimeout());
        assertThat(written.maxFindingsPerCategory()).isEqualTo(defaults.maxFindingsPerCategory());
        assertThat(written.options(RuleId.of("cleancode.long-method")).positiveInteger("max-lines"))
                .contains(60);
        assertThat(second.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(second.err()).contains("already exists");
    }

    @Test
    void aDisabledRuleNoLongerBlocks() {
        cli.write(INJECTABLE, INJECTABLE_SOURCE);

        Result result = checkWithSettings("rules:\n  disabled: [security.sql-concatenation]\n");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out()).contains("settings from spring-inspector.yml");
    }

    @Test
    void severityOverridesAndTheGateDecideTogether() {
        cli.write(INJECTABLE, INJECTABLE_SOURCE);

        Result lowered = checkWithSettings("rules:\n  severity:\n    security.sql-concatenation: minor\n");
        Result stricter = checkWithSettings("""
                gate:
                  block-on: minor
                rules:
                  severity:
                    security.sql-concatenation: minor
                """);

        assertThat(lowered.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(lowered.out()).contains("MINOR   src/main/java/com/acme/Orders.java:9");
        assertThat(stricter.exitCode()).isEqualTo(ExitCodes.BLOCKED);
    }

    @Test
    void ignoredFilesAreNotInspected() {
        cli.write(INJECTABLE, INJECTABLE_SOURCE);

        assertThat(checkWithSettings("ignore: [\"**/acme/Orders.java\"]\n").exitCode())
                .isEqualTo(ExitCodes.OK);
    }

    @Test
    void ruleOptionsReachTheRule() {
        cli.write("src/main/java/com/acme/Busy.java", """
                package com.acme;

                class Busy {
                    int work() {
                        int a = 1;
                        int b = 2;
                        return a + b;
                    }
                }
                """);

        Result result = checkWithSettings("rules:\n  options:\n    cleancode.long-method:\n      max-lines: 2\n");

        assertThat(result.out()).contains("work has 3 lines of code; the limit is 2");
    }

    @Test
    void aJustifiedSuppressionSilencesTheFindingButStaysVisible() {
        cli.write(INJECTABLE, SUPPRESSED_SOURCE).stage();

        Result terminal = cli.run("check", "--details", "security");
        Result json = cli.run("check", "--format", "json");
        Result sarif = cli.run("check", "--format", "sarif");

        assertThat(terminal.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(terminal.out())
                .contains("1 suppressed with inspector:ignore; reasons below")
                .contains("Suppressed\n    src/main/java/com/acme/Orders.java:10  security.sql-concatenation - id is a"
                        + " validated UUID");
        assertThat(json(json.out()).at("/suppressed/0/reason").asString()).isEqualTo("id is a validated UUID");
        JsonNode result = json(sarif.out()).at("/runs/0/results/0");
        assertThat(result.at("/suppressions/0/kind").asString()).isEqualTo("inSource");
        assertThat(result.at("/suppressions/0/justification").asString()).isEqualTo("id is a validated UUID");
    }

    @Test
    void aSuppressionWithoutAReasonIsReportedAndDoesNotApply() {
        cli.write(INJECTABLE, SUPPRESSED_SOURCE.replace(" reason=\"id is a validated UUID\"", ""))
                .stage();

        Result result = cli.run("check");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.BLOCKED);
        assertThat(result.out())
                .contains("inspector:ignore at src/main/java/com/acme/Orders.java:9 was not applied: it needs"
                        + " reason=\"...\"");
        assertThat(json(cli.run("check", "--format", "json").out())
                        .at("/suppressionsWithoutReason/0/line")
                        .asInt())
                .isEqualTo(9);
    }

    @Test
    void warnsAboutUnknownSettingsAndRules() {
        Result result = checkWithSettings("""
                tone: sensei
                rules:
                  disabled: [security.sql-concatenaton]
                """);

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.err())
                .contains("warning: spring-inspector.yml: unknown setting 'tone' is ignored")
                .contains("warning: spring-inspector.yml: no rule 'security.sql-concatenaton' exists");
    }

    @Test
    void stopsOnAnInvalidSetting() {
        Result result = checkWithSettings("gate:\n  block-on: critical\n");

        assertThat(result.exitCode()).isEqualTo(ExitCodes.USAGE);
        assertThat(result.err()).contains("error: spring-inspector.yml: gate.block-on must be one of");
    }

    @Test
    void anExplicitSettingsFileReplacesTheProjectsOwn() {
        cli.write(INJECTABLE, INJECTABLE_SOURCE);
        cli.write("ci/strict.yml", "rules:\n  disabled: [security.sql-concatenation]\n");

        Result result = checkWithSettings(
                "{}\n", "--config", cli.project().resolve("ci/strict.yml").toString());

        assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
        assertThat(result.out()).contains("settings from strict.yml");
    }
}
