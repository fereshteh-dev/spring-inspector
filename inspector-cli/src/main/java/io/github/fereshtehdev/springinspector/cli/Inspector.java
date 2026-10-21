package io.github.fereshtehdev.springinspector.cli;

import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.change.ChangeScope;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.engine.InspectionEngine;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.CollectedChanges;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import io.github.fereshtehdev.springinspector.core.port.ProjectDetector;
import io.github.fereshtehdev.springinspector.git.GitWorkspace;
import io.github.fereshtehdev.springinspector.javamodel.JavaModel;
import io.github.fereshtehdev.springinspector.javamodel.cache.CacheSecret;
import io.github.fereshtehdev.springinspector.javamodel.cache.FindingCache;
import io.github.fereshtehdev.springinspector.project.config.ConfigFileReader;
import io.github.fereshtehdev.springinspector.project.config.LoadedConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/** One inspection run, wired from the adapters: detect the project, collect changes, inspect, keep the cache. */
final class Inspector {

    private final ProjectDetector detector;
    private final List<Check> checks;
    private final Map<String, String> environment;
    private final InspectorHome home;
    private final ConfigFileReader configReader = new ConfigFileReader();

    Inspector(ProjectDetector detector, List<Check> checks, Map<String, String> environment, InspectorHome home) {
        this.detector = detector;
        this.checks = List.copyOf(checks);
        this.environment = Map.copyOf(environment);
        this.home = home;
    }

    InspectionRun inspect(Path projectRoot, ChangeScope scope, boolean useCache, Optional<Path> configFile) {
        DetectedProject detected;
        CollectedChanges changes;
        FindingCache cache;
        LoadedConfig loaded;
        // Independent and mostly cold-start work (XML parsing, JGit, reading the cache): overlap it.
        try (ExecutorService setup = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<DetectedProject> detecting = setup.submit(() -> detector.detect(projectRoot));
            Future<LoadedConfig> configuring = setup.submit(
                    () -> configFile.map(configReader::readFile).orElseGet(() -> configReader.read(projectRoot)));
            Future<FindingCache> loadingCache = setup.submit(() -> useCache
                    ? FindingCache.open(projectRoot, CacheSecret.loadOrCreate(home.cacheKey()))
                    : FindingCache.disabled());
            try (GitWorkspace workspace = GitWorkspace.open(projectRoot, environment)) {
                changes = workspace.collect(scope);
            }
            detected = join(detecting);
            cache = join(loadingCache);
            loaded = join(configuring);
        }
        ProjectContext project = detected.withConfig(loaded.config());
        JavaModel model = new JavaModel(project, changes.sources(), cache);
        ReportCard report =
                new InspectionEngine(checks).inspect(new InspectionInput(project, changes.changeSet(), model));
        cache.save();
        List<String> warnings = new ArrayList<>(loaded.warnings());
        warnings.addAll(unknownRules(loaded));
        return new InspectionRun(
                detected,
                report,
                changes.changeSet().files().size(),
                model.unparseableFiles(),
                loaded.file(),
                warnings);
    }

    /** Rule ids in the configuration that no check has, usually a typo that silently does nothing. */
    private List<String> unknownRules(LoadedConfig loaded) {
        Set<RuleId> known = checks.stream().map(Check::id).collect(Collectors.toSet());
        InspectorConfig config = loaded.config();
        Set<RuleId> mentioned = new TreeSet<>(config.disabledRules());
        mentioned.addAll(config.severityOverrides().keySet());
        mentioned.addAll(config.ruleOptions().keySet());
        String source = loaded.file().map(file -> file.getFileName().toString()).orElse("configuration");
        return mentioned.stream()
                .filter(ruleId -> !known.contains(ruleId))
                .map(ruleId -> source + ": no rule '" + ruleId + "' exists; check the spelling")
                .toList();
    }

    /** The task's result, rethrowing its own exception so callers still see what went wrong. */
    private static <T> T join(Future<T> task) {
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while preparing the inspection");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        }
    }
}
