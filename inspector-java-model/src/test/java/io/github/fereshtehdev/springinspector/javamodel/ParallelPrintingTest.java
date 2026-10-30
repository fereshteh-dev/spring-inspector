package io.github.fereshtehdev.springinspector.javamodel;

import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.PROJECT;
import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.main;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.fereshtehdev.springinspector.javamodel.Fixtures.Sources;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Checks run in parallel on one shared syntax tree, and many of them call {@code toString()} on its nodes. */
class ParallelPrintingTest {

    private static final Path FILE = main("com/acme/App.java");
    private static final String SOURCE = """
            package com.acme;

            class App {
                String run(Service service) {
                    return service.first(service.second("a"), service.third("b"));
                }
            }
            """;
    private static final int TASKS = 32;
    private static final int PRINTS_PER_TASK = 200;

    private static CompilationUnit parsed() {
        JavaModel model = new JavaModel(PROJECT, new Sources().put(FILE, SOURCE));
        return model.parse(FILE).orElseThrow().unit();
    }

    @Test
    void parsedFilesHaveNoDataThatMakesPrintingChangeSharedSettings() {
        assertThat(parsed().containsData(Node.LINE_SEPARATOR_KEY)).isFalse();
    }

    @RepeatedTest(5)
    void checksCanPrintNodesOfTheSameFileAtTheSameTime() throws InterruptedException, ExecutionException {
        CompilationUnit unit = parsed();
        List<MethodCallExpr> calls = unit.findAll(MethodCallExpr.class);
        List<Future<?>> tasks = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int task = 0; task < TASKS; task++) {
                tasks.add(executor.submit(() -> {
                    for (int print = 0; print < PRINTS_PER_TASK; print++) {
                        assertThat(calls.getFirst().toString()).startsWith("service.first");
                        assertThat(unit.toString()).contains("class App");
                    }
                }));
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
    }
}
