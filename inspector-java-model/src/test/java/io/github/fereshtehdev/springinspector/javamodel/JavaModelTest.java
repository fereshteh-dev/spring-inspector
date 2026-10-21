package io.github.fereshtehdev.springinspector.javamodel;

import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.PROJECT;
import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.input;
import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.main;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.engine.InspectionEngine;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.javamodel.Fixtures.ScriptedCheck;
import io.github.fereshtehdev.springinspector.javamodel.Fixtures.Sources;
import io.github.fereshtehdev.springinspector.javamodel.cache.CacheSecret;
import io.github.fereshtehdev.springinspector.javamodel.cache.FindingCache;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaModelTest {

    private static final Path APP = main("com/acme/App.java");
    private static final Path GREETER = main("com/acme/Greeter.java");
    private static final Path HELPER = main("com/acme/Helper.java");

    private static final String APP_SOURCE = """
            package com.acme;

            class App {
                String run() {
                    return new Greeter().greet() + new Helper();
                }
            }
            """;

    private static final String GREETER_SOURCE = """
            package com.acme;

            class Greeter {
                String greet() {
                    return "hi";
                }
            }
            """;

    @TempDir
    Path project;

    private final Sources sources = new Sources().put(APP, APP_SOURCE).put(GREETER, GREETER_SOURCE);
    private final CacheSecret secret = CacheSecret.random();

    private static List<String> callTypes(JavaFile file) {
        List<String> types = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            types.add(call.getNameAsString() + ":" + file.declaringTypeOf(call).orElse("?") + ":"
                    + file.typeOf(call).orElse("?"));
        }
        return types;
    }

    private static Optional<String> sameBuild(Check check) {
        return Optional.of(check.id() + "@test");
    }

    /** One run with a cache opened from and saved to disk, as a command-line run would do. */
    private List<Finding> runCached(Check check) {
        return runCached(check, JavaModelTest::sameBuild);
    }

    private static InspectorConfig withOptions(Map<String, String> options) {
        InspectorConfig defaults = InspectorConfig.defaults();
        return new InspectorConfig(
                defaults.disabledCategories(),
                defaults.disabledRules(),
                defaults.severityOverrides(),
                defaults.checkTimeout(),
                defaults.maxFindingsPerCategory(),
                defaults.changedLinesOnly(),
                defaults.gatePolicy(),
                Map.of(RuleId.of("options"), new RuleOptions(options)),
                List.of());
    }

    private List<Finding> runCached(Check check, InspectorConfig config) {
        FindingCache cache = FindingCache.open(project, secret);
        ProjectContext configured =
                new ProjectContext(PROJECT.root(), PROJECT.versions(), PROJECT.buildTool(), PROJECT.modules(), config);
        JavaModel model = new JavaModel(configured, sources, cache, JavaModelTest::sameBuild);
        InspectionInput input = input(model, APP);
        List<Finding> findings = check.inspect(new InspectionInput(configured, input.changeSet(), input.sources()));
        assertThat(cache.save()).isTrue();
        return findings;
    }

    private List<Finding> runCached(Check check, CheckFingerprints fingerprints) {
        FindingCache cache = FindingCache.open(project, secret);
        List<Finding> findings = run(new JavaModel(PROJECT, sources, cache, fingerprints), check, APP);
        assertThat(cache.save()).isTrue();
        return findings;
    }

    private static List<Finding> run(JavaModel model, Check check, Path... files) {
        return check.inspect(input(model, files));
    }

    @Test
    void resolvesTypesDeclaredInOtherProjectFilesAndTheJdk() {
        ScriptedCheck check = new ScriptedCheck("types", JavaModelTest::callTypes);

        List<Finding> findings = run(new JavaModel(PROJECT, sources), check, APP);

        assertThat(findings)
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("greet:com.acme.Greeter:java.lang.String");
    }

    @Test
    void typesThatCannotBeResolvedAreEmptyInsteadOfFailingTheCheck() {
        sources.put(APP, """
                package com.acme;

                import org.example.Missing;

                class App {
                    Object run(Missing missing) {
                        return missing.call();
                    }
                }
                """);

        List<Finding> findings =
                run(new JavaModel(PROJECT, sources), new ScriptedCheck("types", JavaModelTest::callTypes), APP);

        assertThat(findings)
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("call:?:?");
    }

    @Test
    void parsesEachFileOnceAndSharesTheTreeBetweenChecks() {
        JavaModel model = new JavaModel(PROJECT, sources);
        Set<CompilationUnit> units = ConcurrentHashMap.newKeySet();
        ScriptedCheck first = new ScriptedCheck("first", file -> {
            units.add(file.unit());
            return List.of();
        });
        ScriptedCheck second = new ScriptedCheck("second", file -> {
            units.add(file.unit());
            return List.of();
        });

        new InspectionEngine(List.of(first, second)).inspect(input(model, APP));

        assertThat(units).hasSize(1);
        assertThat(sources.reads()).isEqualTo(1);
    }

    @Test
    void anUnparseableFileProducesNoFindingsAndIsReported() {
        sources.put(APP, "class App { void broken( }");

        List<Finding> findings =
                run(new JavaModel(PROJECT, sources), new ScriptedCheck("any", file -> List.of("x")), APP);

        assertThat(findings).isEmpty();
    }

    @Test
    void unparseableFilesAreListedWithTheReason() {
        sources.put(APP, "class App { void broken( }");
        JavaModel model = new JavaModel(PROJECT, sources);

        run(model, new ScriptedCheck("any", file -> List.of()), APP);

        assertThat(model.unparseableFiles()).containsOnlyKeys(APP);
    }

    @Test
    void oversizedFilesAreNotParsed() {
        sources.put(APP, "class App {}" + " ".repeat(JavaModel.MAX_SOURCE_CHARS));
        JavaModel model = new JavaModel(PROJECT, sources);

        List<Finding> findings = run(model, new ScriptedCheck("any", file -> List.of("x")), APP);

        assertThat(findings).isEmpty();
        assertThat(model.unparseableFiles().get(APP)).contains("larger than");
    }

    @Test
    void onlyJavaFilesThatExistAreInspected() {
        ScriptedCheck check = new ScriptedCheck("any", file -> List.of("x"));

        List<Finding> findings = run(
                new JavaModel(PROJECT, sources),
                check,
                Path.of("src", "main", "resources", "application.yml"),
                main("com/acme/Deleted.java"));

        assertThat(findings).isEmpty();
        assertThat(check.inspected()).isZero();
    }

    @Test
    void checksGivenAPlainSourceProviderStillWork() {
        ScriptedCheck check = new ScriptedCheck("any", file -> List.of("x"));

        assertThat(check.inspect(input(sources, APP))).hasSize(1);
    }

    @Test
    void theSharedModelIsUsedWhenTheRunProvidesIt() {
        JavaModel model = new JavaModel(PROJECT, sources);
        InspectionInput input = input(model, APP);

        assertThat(JavaModel.of(input)).isSameAs(model);
        assertThat(JavaModel.of(input(sources, APP))).isNotSameAs(model);
    }

    @Test
    void aSecondRunReusesCachedFindingsWithoutParsingOrChecking() {
        List<Finding> first = runCached(new ScriptedCheck("types", JavaModelTest::callTypes));

        ScriptedCheck again = new ScriptedCheck("types", JavaModelTest::callTypes);
        List<Finding> second = runCached(again);

        assertThat(second).isEqualTo(first);
        assertThat(again.inspected()).isZero();
    }

    @Test
    void changingAResolvedDependencyInvalidatesTheCachedResult() {
        runCached(new ScriptedCheck("types", JavaModelTest::callTypes));
        sources.put(
                GREETER,
                GREETER_SOURCE.replace("String greet()", "Integer greet()").replace("\"hi\"", "1"));

        ScriptedCheck again = new ScriptedCheck("types", JavaModelTest::callTypes);
        List<Finding> second = runCached(again);

        assertThat(again.inspected()).isEqualTo(1);
        assertThat(second)
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("greet:com.acme.Greeter:java.lang.Integer");
    }

    @Test
    void addingAFileForATypeThatWasMissingInvalidatesTheCachedResult() {
        assertThat(runCached(new ScriptedCheck("helper", JavaModelTest::helperType)))
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("?");
        sources.put(HELPER, """
                package com.acme;

                class Helper {}
                """);

        ScriptedCheck again = new ScriptedCheck("helper", JavaModelTest::helperType);
        List<Finding> second = runCached(again);

        assertThat(again.inspected()).isEqualTo(1);
        assertThat(second)
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("com.acme.Helper");
    }

    @Test
    void checksReadTheirOptionsAndChangingThemInvalidatesTheCachedResult() {
        Check reportsOptions =
                new ScriptedCheck("options", file -> List.of(file.options().fingerprint()));
        InspectorConfig eighty = withOptions(Map.of("max-lines", "80"));
        InspectorConfig ninety = withOptions(Map.of("max-lines", "90"));

        List<Finding> first = runCached(reportsOptions, eighty);
        ScriptedCheck again =
                new ScriptedCheck("options", file -> List.of(file.options().fingerprint()));
        List<Finding> second = runCached(again, ninety);

        assertThat(first)
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("max-lines=80");
        assertThat(second)
                .extracting(finding -> finding.whatsWrong().description())
                .containsExactly("max-lines=90");
        assertThat(again.inspected()).isEqualTo(1);
    }

    @Test
    void checksWhoseBuildCannotBeIdentifiedAreNeverCached() {
        runCached(new ScriptedCheck("types", JavaModelTest::callTypes), check -> Optional.empty());

        ScriptedCheck again = new ScriptedCheck("types", JavaModelTest::callTypes);
        runCached(again, check -> Optional.empty());

        assertThat(again.inspected()).isEqualTo(1);
    }

    @Test
    void checksRunningInParallelResolveTypesSafely() {
        JavaModel model = new JavaModel(PROJECT, sources);
        List<Check> checks = IntStream.range(0, 32)
                .<Check>mapToObj(i -> new ScriptedCheck("types-" + i, JavaModelTest::callTypes))
                .toList();

        ReportCard report = new InspectionEngine(checks).inspect(input(model, APP, GREETER));

        assertThat(report.problems()).isEmpty();
        assertThat(report.categories().getFirst().findings())
                .extracting(finding -> finding.whatsWrong().description())
                .containsOnly("greet:com.acme.Greeter:java.lang.String");
    }

    private static List<String> helperType(JavaFile file) {
        Expression helper = file.unit()
                .findFirst(
                        ObjectCreationExpr.class,
                        creation -> creation.getType().getNameAsString().equals("Helper"))
                .orElseThrow();
        return List.of(file.typeOf(helper).orElse("?"));
    }
}
