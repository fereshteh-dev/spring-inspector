package io.github.fereshtehdev.springinspector.javamodel;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.core.port.SourceProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

final class Fixtures {

    static final ProjectContext PROJECT = new ProjectContext(
            Path.of("."), DetectedVersions.none(), BuildTool.MAVEN, List.of(), InspectorConfig.defaults());

    private Fixtures() {}

    static Path main(String file) {
        return Path.of("src", "main", "java").resolve(file);
    }

    static InspectionInput input(SourceProvider sources, Path... files) {
        List<FileChange> changes = List.of(files).stream()
                .map(file -> FileChange.entireFile(file.toString()))
                .toList();
        return new InspectionInput(PROJECT, new ChangeSet(changes), sources);
    }

    /** Project files held in memory; counts reads so tests can see what was read again. */
    static final class Sources implements SourceProvider {

        private final Map<Path, String> files = new ConcurrentHashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        Sources put(Path path, String content) {
            files.put(path.normalize(), content);
            return this;
        }

        Sources remove(Path path) {
            files.remove(path.normalize());
            return this;
        }

        int reads() {
            return reads.get();
        }

        @Override
        public Optional<SourceFile> read(Path path) {
            reads.incrementAndGet();
            return Optional.ofNullable(files.get(path.normalize())).map(content -> new SourceFile(path, content));
        }
    }

    /**
     * A check whose behaviour a test supplies: each message the script returns becomes a finding on the whole file.
     * Counts the files it inspected.
     */
    static final class ScriptedCheck extends JavaCheck {

        private final RuleId id;
        private final Function<JavaFile, List<String>> script;
        private final AtomicInteger inspected = new AtomicInteger();

        ScriptedCheck(String id, Function<JavaFile, List<String>> script) {
            this.id = RuleId.of(id);
            this.script = script;
        }

        int inspected() {
            return inspected.get();
        }

        @Override
        public RuleId id() {
            return id;
        }

        @Override
        public Category category() {
            return Category.CLEAN_CODE;
        }

        @Override
        protected List<Finding> inspect(JavaFile file) {
            inspected.incrementAndGet();
            return script.apply(file).stream()
                    .map(message -> finding(
                            file,
                            file.unit(),
                            Severity.MINOR,
                            Confidence.HIGH,
                            message,
                            new WhyItMatters("Test", List.of()),
                            HowToFix.withoutPatch("Test")))
                    .toList();
        }
    }
}
