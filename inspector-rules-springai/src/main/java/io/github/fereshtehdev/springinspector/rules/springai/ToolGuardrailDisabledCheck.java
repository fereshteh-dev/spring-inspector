package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.expr.MethodCallExpr;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Code that switches off a {@code ToolCallingManager} safeguard: the limits on tool calls per turn, or the rule that
 * the model may only call tools attached to the request. Both have been on by default since Spring AI 2.0.1.
 */
public final class ToolGuardrailDisabledCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.tool-guardrail-disabled");

    private static final String SPRING_AI = "org.springframework.ai.";
    private static final String FALLBACK_METHOD = "resolutionFallbackEnabled";
    private static final String FALLBACK_PROBLEM = "The model may call tools that were not attached to the request";
    private static final Map<String, String> UNLIMITED_METHODS = Map.of(
            "unlimitedCallsPerTool", "A tool can be called without limit in one turn",
            "unlimitedTotalToolCalls", "The model can make unlimited tool calls in one turn");

    private static final WhyItMatters WHY = new WhyItMatters(
            "The model decides which tools to call and how often, and users or retrieved documents can steer it."
                    + " Without limits, a loop can run up cost and load without end. With the resolution fallback, it"
                    + " can reach any tool in the application by name, not only the ones meant for this request.",
            List.of(
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM10 Unbounded Consumption",
                            URI.create("https://genai.owasp.org/llmrisk/llm102025-unbounded-consumption/")),
                    new Reference(
                            "Spring AI: Tool calling",
                            URI.create("https://docs.spring.io/spring-ai/reference/api/tools.html"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Remove this call to keep the defaults. If a tool really needs more calls, raise its limit with"
                    + " maxCallsPerTool(name, limit), and attach the tools a request needs with .tools(...).");

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
        boolean usesSpringAi = file.unit().getImports().stream()
                .anyMatch(declaration -> declaration.getNameAsString().startsWith(SPRING_AI));
        if (!usesSpringAi) {
            return List.of();
        }
        return file.unit().findAll(MethodCallExpr.class).stream()
                .flatMap(call -> problem(call)
                        .map(problem ->
                                finding(file, call.getName(), Severity.MAJOR, Confidence.HIGH, problem, WHY, FIX))
                        .stream())
                .toList();
    }

    private static Optional<String> problem(MethodCallExpr call) {
        String method = call.getNameAsString();
        if (method.equals(FALLBACK_METHOD)
                && call.getArguments().size() == 1
                && call.getArgument(0).toString().equals("true")) {
            return Optional.of(FALLBACK_PROBLEM);
        }
        return Optional.ofNullable(UNLIMITED_METHODS.get(method));
    }
}
