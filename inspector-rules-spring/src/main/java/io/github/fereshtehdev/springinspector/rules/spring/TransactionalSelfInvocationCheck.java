package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A method calling a {@code @Transactional} method of its own class while not being transactional itself: the call
 * bypasses the proxy, so no transaction starts.
 */
public final class TransactionalSelfInvocationCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.transactional-self-invocation");

    private static final WhyItMatters WHY = new WhyItMatters(
            "@Transactional works through a proxy around the bean. A call from inside the same object does not go"
                    + " through that proxy, so the called method runs without the transaction it asks for.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Understanding AOP proxies",
                    "spring-framework/reference/core/aop/proxying.html#aop-understanding-aop-proxies")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Put @Transactional on the method that is called from outside, or move the transactional method into its"
                    + " own bean and call it through that bean.");

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
            if (SpringCode.isTransactional(file, type)) {
                continue;
            }
            Set<String> transactional = type.getMethods().stream()
                    .filter(method -> SpringCode.isTransactional(file, method))
                    .map(MethodDeclaration::getNameAsString)
                    .collect(Collectors.toSet());
            if (transactional.isEmpty()) {
                continue;
            }
            for (MethodDeclaration caller : type.getMethods()) {
                if (SpringCode.isTransactional(file, caller)) {
                    continue;
                }
                for (MethodCallExpr call : caller.findAll(MethodCallExpr.class)) {
                    if (callsOwnMethod(call, type)
                            && transactional.contains(call.getNameAsString())
                            && !call.getNameAsString().equals(caller.getNameAsString())) {
                        findings.add(finding(
                                file,
                                call,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                caller.getNameAsString() + "() calls @Transactional " + call.getNameAsString()
                                        + "() on the same object, so no transaction starts",
                                WHY,
                                FIX));
                    }
                }
            }
        }
        return findings;
    }

    private static boolean callsOwnMethod(MethodCallExpr call, ClassOrInterfaceDeclaration type) {
        boolean sameObject =
                call.getScope().map(scope -> scope instanceof ThisExpr).orElse(true);
        Optional<ClassOrInterfaceDeclaration> enclosing = Nodes.ancestor(call, ClassOrInterfaceDeclaration.class);
        return sameObject && enclosing.map(found -> found.equals(type)).orElse(false);
    }
}
