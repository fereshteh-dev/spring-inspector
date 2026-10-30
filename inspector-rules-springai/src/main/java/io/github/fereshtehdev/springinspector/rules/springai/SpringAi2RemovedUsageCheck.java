package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Methods and constants that the Spring AI 2.0 upgrade notes list as removed, in a project on 2.0 or later. Their
 * classes still exist, so these uses are not caught by looking at imports alone.
 */
public final class SpringAi2RemovedUsageCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("migration.spring-ai-2-removed-usage");

    private static final String SPRING_AI = "org.springframework.ai.";
    private static final String CONVERSATION_ID_CONSTANT = "DEFAULT_CONVERSATION_ID";
    private static final String CONVERSATION_ID_FIX =
            "Pass the caller's own id with .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id)).";
    private static final Set<String> MEMORY_ADVISORS =
            Set.of("MessageChatMemoryAdvisor", "VectorStoreChatMemoryAdvisor");
    private static final Set<String> REMOVED_JSON_HELPERS = Set.of("jsonToMap", "jsonToObject", "toJsonString");

    private static final Map<String, String> REMOVED_METHODS = Map.of(
            "internalToolExecutionEnabled",
                    "Use ChatClient, which runs tools through ToolCallingAdvisor, or drive the loop with"
                            + " ToolCallingManager.",
            "toolNames", "Declare ToolCallback beans or @Tool methods and attach them with .tools(...).",
            "functionCallbacks", "Use toolCallbacks(...).",
            "defaultFunctions", "Use defaultTools(...).",
            "functions", "Use tools(...).");

    private static final WhyItMatters WHY = new WhyItMatters(
            "This was removed in Spring AI 2.0, so the file does not compile against the version the project uses.",
            List.of(new Reference(
                    "Spring AI upgrade notes",
                    URI.create("https://docs.spring.io/spring-ai/reference/upgrade-notes.html"))));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.MIGRATION;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringSpringAi(VersionRange.atLeast(Version.of(2, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        boolean usesSpringAi = file.unit().getImports().stream()
                .anyMatch(declaration -> declaration.getNameAsString().startsWith(SPRING_AI));
        if (!usesSpringAi) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            String method = call.getNameAsString();
            if (REMOVED_METHODS.containsKey(method)) {
                findings.add(report(file, call.getName(), method + "(...)", REMOVED_METHODS.get(method)));
            } else if (method.equals("conversationId") && isOnMemoryAdvisorBuilder(call)) {
                findings.add(report(file, call.getName(), "conversationId(...) on the builder", CONVERSATION_ID_FIX));
            } else if (REMOVED_JSON_HELPERS.contains(method) && isOnModelOptionsUtils(call)) {
                findings.add(report(
                        file,
                        call.getName(),
                        "ModelOptionsUtils." + method + "(...)",
                        "Use JsonHelper, as the upgrade notes describe for " + method + "."));
            }
        }
        for (FieldAccessExpr field : file.unit().findAll(FieldAccessExpr.class)) {
            if (field.getNameAsString().equals(CONVERSATION_ID_CONSTANT)) {
                findings.add(report(file, field, CONVERSATION_ID_CONSTANT, CONVERSATION_ID_FIX));
            }
        }
        for (NameExpr name : file.unit().findAll(NameExpr.class)) {
            if (name.getNameAsString().equals(CONVERSATION_ID_CONSTANT)) {
                findings.add(report(file, name, CONVERSATION_ID_CONSTANT, CONVERSATION_ID_FIX));
            }
        }
        return findings;
    }

    private static boolean isOnMemoryAdvisorBuilder(MethodCallExpr call) {
        return call.getScope()
                .map(scope -> MEMORY_ADVISORS.stream().anyMatch(scope.toString()::contains))
                .orElse(false);
    }

    private static boolean isOnModelOptionsUtils(MethodCallExpr call) {
        return call.getScope()
                .filter(scope -> scope.toString().equals("ModelOptionsUtils"))
                .isPresent();
    }

    private Finding report(JavaFile file, Node use, String removed, String fix) {
        return finding(
                file,
                use,
                Severity.MAJOR,
                Confidence.HIGH,
                removed + " was removed in Spring AI 2.0",
                WHY,
                HowToFix.withoutPatch(fix));
    }
}
