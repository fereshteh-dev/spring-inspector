package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code SimpleLoggerAdvisor} with its default formatting, which logs every prompt and response in full. An advisor
 * given its own request and response formatters is not reported.
 */
public final class FullPromptLoggingCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.full-prompt-logging");

    private static final String ADVISOR = "org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor";

    private static final WhyItMatters WHY = new WhyItMatters(
            "Prompts and answers carry what users typed and what the application retrieved for them: names, emails,"
                    + " health or account data. Logged in full, that personal data spreads into log storage that is"
                    + " neither protected nor deleted like the original.",
            List.of(
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM02 Sensitive Information Disclosure",
                            URI.create("https://genai.owasp.org/llmrisk/llm022025-sensitive-information-disclosure/")),
                    new Reference(
                            "Spring AI: Logging advisor",
                            URI.create("https://docs.spring.io/spring-ai/reference/api/advisors.html#_logging"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Build the advisor with SimpleLoggerAdvisor.builder().requestToString(...).responseToString(...) and log"
                    + " only metadata or redacted text, or enable it only in development profiles.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_AI;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Node> defaults = new ArrayList<>();
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            if (file.refersTo(creation.getType().getNameWithScope(), ADVISOR)
                    && creation.getArguments().size() <= 1) {
                defaults.add(creation);
            }
        }
        for (MethodCallExpr build : file.unit()
                .findAll(MethodCallExpr.class, call -> call.getNameAsString().equals("build"))) {
            if (startsFromAdvisorBuilder(file, build) && !chainCalls(build, "requestToString")) {
                defaults.add(build);
            }
        }
        return defaults.stream()
                .map(node -> finding(
                        file,
                        node,
                        Severity.MINOR,
                        Confidence.HIGH,
                        "SimpleLoggerAdvisor logs full prompts and responses",
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean startsFromAdvisorBuilder(JavaFile file, MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        while (scope.isPresent() && scope.get() instanceof MethodCallExpr inner) {
            if (inner.getNameAsString().equals("builder")) {
                return inner.getScope()
                        .map(type -> file.refersTo(type.toString(), ADVISOR))
                        .orElse(false);
            }
            scope = inner.getScope();
        }
        return false;
    }

    private static boolean chainCalls(MethodCallExpr call, String name) {
        Optional<Expression> scope = call.getScope();
        while (scope.isPresent() && scope.get() instanceof MethodCallExpr inner) {
            if (inner.getNameAsString().equals(name)) {
                return true;
            }
            scope = inner.getScope();
        }
        return false;
    }
}
