package io.github.fereshtehdev.springinspector.rules.security;

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
import java.util.List;

/** Spring Expression Language text parsed from request input, which lets the caller run code in the application. */
public final class SpelInjectionCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.spel-injection");

    private static final String SPRING_EXPRESSION = "org.springframework.expression";

    private static final WhyItMatters WHY = new WhyItMatters(
            "SpEL can create objects and call methods. With a standard evaluation context, an expression chosen by the"
                    + " caller can start programs on the server, so this is remote code execution.",
            List.of(
                    SecurityReferences.cwe(917, "Expression language injection"),
                    SecurityReferences.springDocs(
                            "Spring Framework: Evaluation contexts",
                            "spring-framework/reference/core/expressions/evaluation.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Do not parse text from the request. Keep expressions fixed in code and pass request values in as"
                    + " variables. If callers must choose among behaviours, map their input to an allow-list of fixed"
                    + " expressions, and evaluate with SimpleEvaluationContext.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        boolean usesSpel = file.unit().getImports().stream()
                .anyMatch(declaration -> declaration.getNameAsString().startsWith(SPRING_EXPRESSION));
        if (!usesSpel) {
            return List.of();
        }
        return file.unit().findAll(MethodCallExpr.class, call -> parsesRequestInput(file, call)).stream()
                .map(call -> finding(
                        file,
                        call,
                        Severity.BLOCKER,
                        Confidence.HIGH,
                        "A SpEL expression is parsed from request input",
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean parsesRequestInput(JavaFile file, MethodCallExpr call) {
        return call.getNameAsString().equals("parseExpression")
                && !call.getArguments().isEmpty()
                && RequestInputs.isRequestInput(file, call.getArgument(0));
    }
}
