package io.github.fereshtehdev.springinspector.core.engine;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class CheckRunner {

    private final Duration timeout;

    CheckRunner(Duration timeout) {
        this.timeout = timeout;
    }

    CheckRun run(List<Check> checks, InspectionInput input) {
        List<Callable<List<Finding>>> tasks = checks.stream()
                .<Callable<List<Finding>>>map(check -> () -> check.inspect(input))
                .toList();
        // Not try-with-resources: close() would wait for checks that ignore interruption.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            return collect(checks, executor.invokeAll(tasks, timeout.toNanos(), TimeUnit.NANOSECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Inspection was interrupted");
        } finally {
            executor.shutdownNow();
        }
    }

    private CheckRun collect(List<Check> checks, List<Future<List<Finding>>> futures) {
        List<Finding> findings = new ArrayList<>();
        List<CheckProblem> problems = new ArrayList<>();
        for (int i = 0; i < checks.size(); i++) {
            Check check = checks.get(i);
            Future<List<Finding>> future = futures.get(i);
            switch (future.state()) {
                case SUCCESS -> findings.addAll(future.resultNow());
                case FAILED -> problems.add(new CheckProblem.Failed(check.id(), describe(future.exceptionNow())));
                case CANCELLED, RUNNING -> problems.add(new CheckProblem.TimedOut(check.id(), timeout));
            }
        }
        return new CheckRun(findings, problems);
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        String type = failure.getClass().getSimpleName();
        return message == null ? type : type + ": " + message;
    }
}
