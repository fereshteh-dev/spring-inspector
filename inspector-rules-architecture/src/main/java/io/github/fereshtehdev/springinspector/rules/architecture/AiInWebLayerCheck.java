package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import io.github.fereshtehdev.springinspector.core.domain.project.ProjectContext;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Controllers that use a Spring AI client directly, which puts prompts and model handling in the web layer. */
public final class AiInWebLayerCheck implements Check {

    static final RuleId ID = RuleId.of("architecture.ai-in-web-layer");

    private static final List<String> AI_CLIENTS = List.of(
            "org.springframework.ai.chat.client.ChatClient",
            "org.springframework.ai.chat.model.ChatModel",
            "org.springframework.ai.embedding.EmbeddingModel",
            "org.springframework.ai.vectorstore.VectorStore");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A controller that talks to the model holds the prompts, safety checks and retrieval itself. Other entry"
                    + " points, such as jobs or message listeners, cannot reuse them, and they cannot be tested or"
                    + " changed without the web layer.",
            List.of(new Reference(
                    "Spring Boot reference: structuring your code",
                    URI.create("https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html"))));

    private final Function<ProjectContext, ImportGraph> graphs;

    public AiInWebLayerCheck() {
        this(project -> ImportGraph.empty());
    }

    public AiInWebLayerCheck(Function<ProjectContext, ImportGraph> graphs) {
        this.graphs = graphs;
    }

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.ARCHITECTURE;
    }

    @Override
    public List<Finding> inspect(InspectionInput input) {
        if (!ArchitectureFindings.touchesJava(input)) {
            return List.of();
        }
        Architecture architecture = new Architecture(graphs.apply(input.project()));
        List<Finding> findings = new ArrayList<>();
        for (JavaUnit unit : architecture.units()) {
            if (architecture.layer(unit) != Layer.WEB) {
                continue;
            }
            for (String client : AI_CLIENTS) {
                unit.importOf(client)
                        .map(statement -> ArchitectureFindings.onImport(
                                ID,
                                Severity.MINOR,
                                Confidence.MEDIUM,
                                unit,
                                statement,
                                unit.typeName() + " uses " + simpleName(client) + " directly from the web layer",
                                WHY,
                                "Move the prompts and the model call into a service, and let " + unit.typeName()
                                        + " handle only HTTP."))
                        .ifPresent(findings::add);
            }
        }
        return findings;
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }
}
