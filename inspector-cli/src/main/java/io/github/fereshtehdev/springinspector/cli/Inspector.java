package io.github.fereshtehdev.springinspector.cli;

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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One inspection run, wired from the adapters: detect the project, collect changes, inspect, keep the cache. */
final class Inspector {

    private final ProjectDetector detector;
    private final List<Check> checks;
    private final Map<String, String> environment;
    private final InspectorHome home;

    Inspector(ProjectDetector detector, List<Check> checks, Map<String, String> environment, InspectorHome home) {
        this.detector = detector;
        this.checks = List.copyOf(checks);
        this.environment = Map.copyOf(environment);
        this.home = home;
    }

    InspectionRun inspect(Path projectRoot, ChangeScope scope, boolean useCache) {
        DetectedProject detected;
        CollectedChanges changes;
        FindingCache cache;
        // Independent and mostly cold-start work (XML parsing, JGit, reading the cache): overlap it.
        try (ExecutorService setup = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<DetectedProject> detecting = setup.submit(() -> detector.detect(projectRoot));
            Future<FindingCache> loadingCache = setup.submit(() -> useCache
                    ? FindingCache.open(projectRoot, CacheSecret.loadOrCreate(home.cacheKey()))
                    : FindingCache.disabled());
            try (GitWorkspace workspace = GitWorkspace.open(projectRoot, environment)) {
                changes = workspace.collect(scope);
            }
            detected = join(detecting);
            cache = join(loadingCache);
        }
        ProjectContext project = detected.withConfig(InspectorConfig.defaults());
        JavaModel model = new JavaModel(project, changes.sources(), cache);
        ReportCard report =
                new InspectionEngine(checks).inspect(new InspectionInput(project, changes.changeSet(), model));
        cache.save();
        return new InspectionRun(detected, report, changes.changeSet().files().size(), model.unparseableFiles());
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
