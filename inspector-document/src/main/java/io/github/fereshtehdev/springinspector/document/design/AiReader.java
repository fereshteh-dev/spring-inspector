package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiComponent;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** How a type uses Spring AI: models, memory, vector stores, advisors and the tools it offers to a model. */
final class AiReader {

    private static final Map<String, String> PARTS = parts();
    private static final Set<String> ADVISOR_CONTRACTS =
            Set.of("Advisor", "BaseAdvisor", "CallAdvisor", "StreamAdvisor", "BaseChatMemoryAdvisor");

    private AiReader() {}

    static Optional<AiComponent> component(TypeDeclaration<?> type, Set<String> referenced) {
        Set<String> uses = new LinkedHashSet<>();
        PARTS.forEach((name, part) -> {
            if (referenced.contains(name)) {
                uses.add(part);
            }
        });
        return uses.isEmpty()
                ? Optional.empty()
                : Optional.of(new AiComponent(type.getNameAsString(), List.copyOf(uses)));
    }

    static List<AiTool> tools(TypeDeclaration<?> type) {
        List<AiTool> tools = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String kind = switch (annotation.getName().getIdentifier()) {
                    case "Tool" -> "Tool";
                    case "McpTool" -> "MCP tool";
                    default -> "";
                };
                if (!kind.isEmpty()) {
                    tools.add(new AiTool(
                            kind,
                            Annotations.value(annotation, "name").orElse(method.getNameAsString()),
                            Annotations.value(annotation, "description").orElse(""),
                            type.getNameAsString()));
                }
            }
        }
        return tools;
    }

    static List<String> advisors(Set<String> referenced) {
        return referenced.stream()
                .filter(name -> name.endsWith("Advisor") && !ADVISOR_CONTRACTS.contains(name))
                .sorted()
                .toList();
    }

    static List<String> vectorStores(Set<String> referenced) {
        return referenced.stream()
                .filter(name -> name.endsWith("VectorStore") && !name.equals("VectorStore"))
                .sorted()
                .toList();
    }

    private static Map<String, String> parts() {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("ChatClient", "chat client");
        parts.put("ChatModel", "chat model");
        parts.put("StreamingChatModel", "chat model");
        parts.put("EmbeddingModel", "embedding model");
        parts.put("ImageModel", "image model");
        parts.put("VectorStore", "vector store");
        parts.put("ChatMemory", "chat memory");
        parts.put("ToolCallback", "tool callbacks");
        parts.put("ToolCallbackProvider", "tool callbacks");
        parts.put("McpSyncClient", "MCP client");
        parts.put("McpAsyncClient", "MCP client");
        return parts;
    }
}
