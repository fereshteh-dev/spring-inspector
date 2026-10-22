package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A call from inside a bean to its own {@code @Async}, caching or {@code @Retryable} method: the call bypasses the
 * proxy, so the annotation does nothing. {@code @Transactional} has its own rule.
 */
public final class ProxySelfInvocationCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.proxy-self-invocation");

    private record ProxiedAnnotation(String qualifiedName, String simpleName, String lostEffect) {}

    private static final List<ProxiedAnnotation> ANNOTATIONS = List.of(
            new ProxiedAnnotation(
                    "org.springframework.scheduling.annotation.Async", "Async", "it runs on the caller's thread"),
            new ProxiedAnnotation(
                    "org.springframework.cache.annotation.Cacheable", "Cacheable", "the cache is never used"),
            new ProxiedAnnotation(
                    "org.springframework.cache.annotation.CachePut", "CachePut", "the cache is not updated"),
            new ProxiedAnnotation(
                    "org.springframework.cache.annotation.CacheEvict", "CacheEvict", "nothing is evicted"),
            new ProxiedAnnotation(
                    "org.springframework.resilience.annotation.Retryable", "Retryable", "a failure is not retried"),
            new ProxiedAnnotation(
                    "org.springframework.retry.annotation.Retryable", "Retryable", "a failure is not retried"));

    private static final WhyItMatters WHY = new WhyItMatters(
            "@Async, @Cacheable, @CachePut, @CacheEvict and @Retryable work through a proxy around the bean. A call"
                    + " from inside the same object does not go through that proxy, so the method runs as a plain"
                    + " method call: synchronously, without the cache, without retries.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Understanding AOP proxies",
                    "spring-framework/reference/core/aop/proxying.html#aop-understanding-aop-proxies")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Move the annotated method into its own bean and call it through that bean, or call it from outside this"
                    + " class.");

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
        for (ClassOrInterfaceDeclaration type : file.unit().findAll(ClassOrInterfaceDeclaration.class)) {
            Map<String, ProxiedAnnotation> proxied = new HashMap<>();
            for (MethodDeclaration method : type.getMethods()) {
                proxiedAnnotation(file, method).ifPresent(found -> proxied.put(method.getNameAsString(), found));
            }
            if (proxied.isEmpty()) {
                continue;
            }
            for (MethodDeclaration caller : type.getMethods()) {
                for (MethodCallExpr call : caller.findAll(MethodCallExpr.class)) {
                    ProxiedAnnotation annotation = proxied.get(call.getNameAsString());
                    if (annotation != null
                            && SpringCode.callsOwnMethod(call, type)
                            && !call.getNameAsString().equals(caller.getNameAsString())) {
                        findings.add(finding(
                                file,
                                call,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                caller.getNameAsString() + "() calls @" + annotation.simpleName() + " "
                                        + call.getNameAsString() + "() on the same object, so "
                                        + annotation.lostEffect(),
                                WHY,
                                FIX));
                    }
                }
            }
        }
        return findings;
    }

    private static Optional<ProxiedAnnotation> proxiedAnnotation(JavaFile file, MethodDeclaration method) {
        return ANNOTATIONS.stream()
                .filter(annotation -> file.hasAnnotation(method, annotation.qualifiedName()))
                .findFirst();
    }
}
