package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Blocking calls inside methods that return {@code Mono} or {@code Flux}: {@code block()}, {@code Thread.sleep}, and
 * JDBC or blocking HTTP clients.
 */
public final class BlockingInReactiveCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.blocking-in-reactive");

    private static final List<String> REACTIVE_TYPES =
            List.of("reactor.core.publisher.Mono", "reactor.core.publisher.Flux");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Reactive code runs on a few event-loop threads shared by all requests. One blocking call stalls every"
                    + " request on that thread, so throughput collapses under load; Reactor may also fail with"
                    + " \"block() is not supported in thread\".",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Reactive core and blocking",
                    "spring-framework/reference/web/webflux/new-framework.html#webflux-concurrency-model")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Compose instead of blocking: flatMap the Mono or Flux, use WebClient and R2DBC, or move unavoidable"
                    + " blocking work to Schedulers.boundedElastic() with subscribeOn.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_BOOT;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (MethodDeclaration method :
                file.unit().findAll(MethodDeclaration.class, method -> isReactive(file, method))) {
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                BlockingCalls.describe(file, call)
                        .ifPresent(blocking -> findings.add(finding(
                                file,
                                call,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                blocking + " blocks inside reactive method " + method.getNameAsString(),
                                WHY,
                                FIX)));
            }
        }
        return findings;
    }

    private static boolean isReactive(JavaFile file, MethodDeclaration method) {
        return method.getType().isClassOrInterfaceType()
                && REACTIVE_TYPES.stream()
                        .anyMatch(type -> file.refersTo(
                                method.getType().asClassOrInterfaceType().getNameWithScope(), type));
    }
}
