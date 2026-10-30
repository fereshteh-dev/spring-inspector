package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.ImportDeclaration;
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
 * Imports of Spring AI 1.x classes that no longer exist in 2.0, in a project on Spring AI 2.0 or later. The list
 * comes from comparing the 1.1.7 and 2.0.2 jars of spring-ai-client-chat, spring-ai-model, spring-ai-commons and
 * spring-ai-mcp, plus the upgrade notes; classes that only moved between jars, keeping their package, are not in it.
 */
public final class SpringAi1xApiCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("migration.spring-ai-1x-api");

    static final Set<String> REMOVED_IN_2_0 = Set.of(
            "org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor",
            "org.springframework.ai.chat.prompt.FunctionPromptTemplate",
            "org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate",
            "org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate",
            "org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver",
            "org.springframework.ai.util.LoggingMarkers",
            "org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer",
            "org.springframework.ai.mcp.customizer.McpSyncClientCustomizer",
            "org.springframework.ai.anthropic.api.AnthropicApi");

    /** The MCP annotations moved to {@code org.springframework.ai.mcp.annotation} in 2.0. */
    private static final String COMMUNITY_MCP = "org.springaicommunity.mcp.";

    private static final WhyItMatters WHY = new WhyItMatters(
            "The class was removed in Spring AI 2.0, so this file does not compile against the version the project"
                    + " uses, or only compiles because an old 1.x jar is still on the class path.",
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
        return file.unit().getImports().stream()
                .filter(declaration ->
                        (!declaration.isAsterisk() && REMOVED_IN_2_0.contains(declaration.getNameAsString()))
                                || (declaration.getNameAsString() + ".").startsWith(COMMUNITY_MCP))
                .map(declaration -> finding(
                        file,
                        declaration,
                        Severity.MAJOR,
                        Confidence.HIGH,
                        simpleName(declaration) + " was removed in Spring AI 2.0",
                        WHY,
                        HowToFix.withoutPatch("Replace it as the Spring AI 2.0 upgrade notes describe for "
                                + simpleName(declaration) + ".")))
                .toList();
    }

    private static String simpleName(ImportDeclaration declaration) {
        String name = declaration.getNameAsString();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
