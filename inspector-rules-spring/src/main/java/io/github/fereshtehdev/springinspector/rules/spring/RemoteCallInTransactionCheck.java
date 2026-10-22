package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
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
import java.util.Optional;

/**
 * An HTTP request or a call to an AI model made inside a transaction, through a client held in a field. One finding per
 * method, on its first such call.
 */
public final class RemoteCallInTransactionCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.remote-call-in-transaction");

    private static final List<String> REMOTE_CLIENTS = List.of(
            "org.springframework.web.client.RestClient",
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient",
            "java.net.http.HttpClient",
            "org.springframework.ai.chat.client.ChatClient",
            "org.springframework.ai.chat.model.ChatModel");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A transaction holds a database connection, and often row locks, until it ends. A remote call inside it"
                    + " keeps both for as long as the other service takes to answer. A slow service then empties the"
                    + " connection pool, and a rollback cannot undo what the remote call already did.",
            List.of(SpringCode.springDocs(
                    "Spring Framework: Declarative transaction management",
                    "spring-framework/reference/data-access/transaction/declarative.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Make the remote call before the transaction starts or after it commits: move it to a method without"
                    + " @Transactional that calls the transactional one, or react to the commit with"
                    + " @TransactionalEventListener.");

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
        return file.unit().findAll(MethodDeclaration.class, method -> isTransactional(file, method)).stream()
                .flatMap(method -> firstRemoteCall(file, method).stream()
                        .map(call -> finding(
                                file,
                                call,
                                Severity.MAJOR,
                                Confidence.MEDIUM,
                                method.getNameAsString() + "() makes a remote call through " + root(call)
                                        + " inside a transaction",
                                WHY,
                                FIX)))
                .toList();
    }

    private static boolean isTransactional(JavaFile file, MethodDeclaration method) {
        return SpringCode.isTransactional(file, method)
                || (method.isPublic()
                        && SpringCode.enclosingType(method)
                                .map(type -> SpringCode.isTransactional(file, type))
                                .orElse(false));
    }

    private static Optional<MethodCallExpr> firstRemoteCall(JavaFile file, MethodDeclaration method) {
        return method.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getScope()
                        .filter(scope -> !scope.isMethodCallExpr())
                        .filter(scope -> SpringCode.hasFieldOfType(file, scope, REMOTE_CLIENTS))
                        .isPresent())
                .findFirst();
    }

    private static String root(MethodCallExpr call) {
        return call.getScope().map(Expression::toString).orElse("a remote client");
    }
}
