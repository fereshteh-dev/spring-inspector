package io.github.fereshtehdev.springinspector.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.javamodel.JavaModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one check against an in-memory project, the way an inspection run would: through a shared {@link JavaModel},
 * with every file changed. Fixtures can be inline text or test resources.
 */
public final class CheckTester {

    private final Check check;
    private final Map<Path, String> files = new LinkedHashMap<>();
    private DetectedVersions versions = DetectedVersions.none();
    private final Map<String, String> options = new LinkedHashMap<>();

    private CheckTester(Check check) {
        this.check = check;
    }

    public static CheckTester of(Check check) {
        return new CheckTester(check);
    }

    /** Reads a test resource, such as a bad or good example of a rule. */
    public static String resource(String name) {
        try (InputStream in = CheckTester.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException("No test resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The one-based line of the first occurrence of {@code text}, so tests need not count fixture lines. */
    public static int lineOf(String content, String text) {
        int index = content.indexOf(text);
        if (index < 0) {
            throw new IllegalArgumentException("Text not found: " + text);
        }
        return (int) content.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }

    public CheckTester file(String path, String content) {
        files.put(Path.of(path).normalize(), content);
        return this;
    }

    /** Adds a Java source under {@code src/main/java}, placed by its package and first type name. */
    public CheckTester source(String content) {
        return file(sourcePath(content), content);
    }

    /** Sets an option of the check under test, as spring-inspector.yml would. */
    public CheckTester option(String key, String value) {
        options.put(key, value);
        return this;
    }

    public CheckTester javaVersion(String version) {
        versions =
                new DetectedVersions(Optional.of(Version.parse(version)), versions.springBoot(), versions.springAi());
        return this;
    }

    public CheckTester springBootVersion(String version) {
        versions = new DetectedVersions(versions.java(), Optional.of(Version.parse(version)), versions.springAi());
        return this;
    }

    public CheckTester springAiVersion(String version) {
        versions = new DetectedVersions(versions.java(), versions.springBoot(), Optional.of(Version.parse(version)));
        return this;
    }

    /** Whether the check applies to the project's versions, as the engine decides before running it. */
    public boolean applies() {
        return check.applicability().appliesTo(versions);
    }

    public List<Finding> findings() {
        InspectorConfig defaults = InspectorConfig.defaults();
        InspectorConfig config = new InspectorConfig(
                defaults.disabledCategories(),
                defaults.disabledRules(),
                defaults.severityOverrides(),
                defaults.checkTimeout(),
                defaults.maxFindingsPerCategory(),
                defaults.changedLinesOnly(),
                defaults.gatePolicy(),
                Map.of(check.id(), new RuleOptions(options)),
                List.of());
        ProjectContext project = new ProjectContext(Path.of("."), versions, BuildTool.MAVEN, List.of(), config);
        List<FileChange> changes = files.keySet().stream()
                .map(path -> FileChange.entireFile(path.toString()))
                .toList();
        JavaModel model = new JavaModel(
                project,
                path -> Optional.ofNullable(files.get(path.normalize())).map(content -> new SourceFile(path, content)));
        List<Finding> findings = check.inspect(new InspectionInput(project, new ChangeSet(changes), model));
        assertThat(model.unparseableFiles()).as("fixture files must parse").isEmpty();
        findings.forEach(finding -> assertThat(finding.ruleId()).isEqualTo(check.id()));
        return findings;
    }

    public Finding singleFinding() {
        List<Finding> findings = findings();
        assertThat(findings).as("findings of " + check.id()).hasSize(1);
        return findings.getFirst();
    }

    /**
     * Applies the finding's patch to its file and returns the result, after checking that the patch applies cleanly
     * and a patched Java file still parses.
     */
    public String fixed(Finding finding) {
        Patch patch = finding.howToFix().patch().orElseThrow(() -> new AssertionError("Finding has no patch"));
        String original = files.get(finding.location().file());
        if (original == null) {
            throw new AssertionError("Finding is in a file the project does not have: " + finding.location());
        }
        String fixed = UnifiedDiffs.apply(original, patch);
        if (finding.location().file().toString().endsWith(".java")) {
            var parsed = new JavaParser(
                            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25))
                    .parse(fixed);
            assertThat(parsed.isSuccessful())
                    .as("patched file parses:%n%s%n%s", fixed, parsed.getProblems())
                    .isTrue();
        }
        return fixed;
    }

    /** A tester for the same project with the finding's fix applied, to show the fix silences the check. */
    public CheckTester afterFix(Finding finding) {
        String fixed = fixed(finding);
        CheckTester after = new CheckTester(check);
        after.files.putAll(files);
        after.files.put(finding.location().file(), fixed);
        after.versions = versions;
        after.options.putAll(options);
        return after;
    }

    private static String sourcePath(String content) {
        String packageName = firstMatch(content, "package\\s+([\\w.]+)\\s*;").orElse("");
        String typeName = firstMatch(content, "(?:class|interface|enum|record)\\s+(\\w+)")
                .orElseThrow(() -> new IllegalArgumentException("Source declares no type"));
        String directory = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        return "src/main/java/" + directory + typeName + ".java";
    }

    private static Optional<String> firstMatch(String content, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
