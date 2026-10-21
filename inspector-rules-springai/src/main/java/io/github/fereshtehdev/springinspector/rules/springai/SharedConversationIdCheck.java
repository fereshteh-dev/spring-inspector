package io.github.fereshtehdev.springinspector.rules.springai;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A constant chat memory conversation id, as in {@code a.param(ChatMemory.CONVERSATION_ID, "default")}: every caller
 * then reads and extends the same conversation, so one user's messages reach another user's prompts.
 */
public final class SharedConversationIdCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("springai.shared-conversation-id");

    private static final String CHAT_MEMORY = "org.springframework.ai.chat.memory.ChatMemory";
    private static final String CONVERSATION_ID = "CONVERSATION_ID";
    private static final String CONVERSATION_ID_VALUE = "chat_memory_conversation_id";
    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Chat memory advisors keep one history per conversation id. With a fixed id, all users share a single"
                    + " history: personal data from one conversation is replayed into everyone else's prompts.",
            List.of(
                    new Reference(
                            "Spring AI: Chat Memory",
                            URI.create("https://docs.spring.io/spring-ai/reference/api/chat-memory.html")),
                    new Reference(
                            "OWASP Top 10 for LLM Applications: LLM02 Sensitive Information Disclosure",
                            URI.create(
                                    "https://genai.owasp.org/llmrisk/llm022025-sensitive-information-disclosure/"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Use an id that belongs to the caller's conversation, such as the authenticated user's id combined with a"
                    + " session or chat id: a.param(ChatMemory.CONVERSATION_ID, conversationId).");

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
        return file
                .unit()
                .findAll(
                        MethodCallExpr.class,
                        call -> call.getNameAsString().equals("param")
                                && call.getArguments().size() == 2
                                && isConversationIdKey(file, call.getArgument(0))
                                && isConstant(call.getArgument(1)))
                .stream()
                .map(call -> finding(
                        file,
                        call,
                        Severity.MAJOR,
                        Confidence.HIGH,
                        "Chat memory uses the fixed conversation id " + call.getArgument(1),
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean isConversationIdKey(JavaFile file, Expression key) {
        return switch (key) {
            case FieldAccessExpr field ->
                field.getNameAsString().equals(CONVERSATION_ID)
                        && file.refersTo(field.getScope().toString(), CHAT_MEMORY);
            case NameExpr name -> name.getNameAsString().equals(CONVERSATION_ID) && isStaticallyImported(file);
            case StringLiteralExpr literal -> literal.getValue().equals(CONVERSATION_ID_VALUE);
            default -> false;
        };
    }

    private static boolean isStaticallyImported(JavaFile file) {
        return file.unit().getImports().stream()
                .anyMatch(declaration -> declaration.isStatic()
                        && (declaration.getNameAsString().equals(CHAT_MEMORY + "." + CONVERSATION_ID)
                                || (declaration.isAsterisk()
                                        && declaration.getNameAsString().equals(CHAT_MEMORY))));
    }

    private static boolean isConstant(Expression value) {
        Expression followed = StringExpressions.followLocal(value);
        return followed instanceof LiteralExpr
                || (followed instanceof NameExpr name
                        && CONSTANT_NAME.matcher(name.getNameAsString()).matches())
                || (followed instanceof FieldAccessExpr field
                        && CONSTANT_NAME.matcher(field.getNameAsString()).matches());
    }
}
