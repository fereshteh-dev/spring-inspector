package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Fixed or cached platform-thread pools on Java 21 and later, where tasks that wait on I/O are better served by a
 * virtual thread each. A hint: pools for CPU-bound work are right as they are.
 */
public final class ThreadPoolForIoCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.thread-pool-for-io");

    private static final Set<String> PLATFORM_POOLS = Set.of("newFixedThreadPool", "newCachedThreadPool");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A pool of platform threads caps how many requests can wait on I/O at once and needs tuning. Virtual"
                    + " threads are cheap enough to create one per task, so blocking code scales without a pool size.",
            List.of(new Reference("JEP 444: Virtual Threads", URI.create("https://openjdk.org/jeps/444"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "If the tasks mostly wait on I/O, use Executors.newVirtualThreadPerTaskExecutor(); limit concurrency with a"
                    + " Semaphore where a downstream system needs it. Keep the pool for CPU-bound work.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.CLEAN_CODE;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringJava(VersionRange.atLeast(Version.of(21, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file
                .unit()
                .findAll(
                        MethodCallExpr.class,
                        call -> PLATFORM_POOLS.contains(call.getNameAsString())
                                && call.getScope()
                                        .map(scope -> file.refersTo(scope.toString(), "java.util.concurrent.Executors"))
                                        .orElse(false))
                .stream()
                .map(call -> finding(
                        file,
                        call,
                        Severity.INFO,
                        Confidence.MEDIUM,
                        "Executors." + call.getNameAsString()
                                + " creates platform threads where virtual threads may fit",
                        WHY,
                        FIX))
                .toList();
    }
}
