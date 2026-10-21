package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
 * {@code @Transactional} where Spring's proxy cannot apply it: on private, static or final methods, and on final
 * classes. Since Spring 6, protected and package-private methods are proxied and are not reported.
 */
public final class TransactionalNotProxiedCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.transactional-not-proxied");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Transactions are applied by a proxy that overrides the bean's methods. Private, static and final methods"
                    + " cannot be overridden, so the annotation is silently ignored: the code runs without a"
                    + " transaction, and a failure halfway leaves partial changes in the database.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Using @Transactional",
                    "spring-framework/reference/data-access/transaction/declarative/annotations.html")));

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
        for (MethodDeclaration method : file.unit().findAll(MethodDeclaration.class)) {
            if (!SpringCode.isTransactional(file, method)) {
                continue;
            }
            String modifier =
                    method.isPrivate() ? "private" : method.isStatic() ? "static" : method.isFinal() ? "final" : "";
            if (!modifier.isEmpty()) {
                findings.add(finding(
                        file,
                        method.getName(),
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "@Transactional on " + modifier + " method " + method.getNameAsString() + " is ignored",
                        WHY,
                        HowToFix.withoutPatch("Make the method non-private, non-static and non-final, or move the"
                                + " transaction to the public method that calls it.")));
            }
        }
        for (ClassOrInterfaceDeclaration type : file.unit().findAll(ClassOrInterfaceDeclaration.class)) {
            boolean transactional = SpringCode.isTransactional(file, type)
                    || type.getMethods().stream().anyMatch(method -> SpringCode.isTransactional(file, method));
            if (transactional && type.hasModifier(Modifier.Keyword.FINAL)) {
                findings.add(finding(
                        file,
                        type.getName(),
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "Final class " + type.getNameAsString() + " cannot be proxied for @Transactional",
                        WHY,
                        HowToFix.withoutPatch("Remove final from the class, or let it implement an interface that"
                                + " callers use.")));
            }
        }
        return findings;
    }
}
