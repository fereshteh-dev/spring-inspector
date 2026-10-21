package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Calls that park the current thread until I/O or another task completes. */
final class BlockingCalls {

    static final List<String> BLOCKING_CLIENTS = List.of(
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "org.springframework.jdbc.core.simple.JdbcClient",
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.client.RestClient",
            "java.net.http.HttpClient");

    private static final Set<String> REACTOR_BLOCKING =
            Set.of("block", "blockFirst", "blockLast", "toIterable", "toStream");

    private BlockingCalls() {}

    /** Why the call blocks, or empty when it does not. */
    static Optional<String> describe(JavaFile file, MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        if (call.getNameAsString().equals("sleep")
                && scope.map(name -> file.refersTo(name.toString(), "java.lang.Thread"))
                        .orElse(false)) {
            return Optional.of("Thread.sleep()");
        }
        if (REACTOR_BLOCKING.contains(call.getNameAsString())
                && call.getArguments().size() <= 1
                && scope.isPresent()) {
            return Optional.of(call.getNameAsString() + "()");
        }
        if (scope.isPresent() && SpringCode.hasFieldOfType(file, scope.get(), BLOCKING_CLIENTS)) {
            return Optional.of(scope.get() + "." + call.getNameAsString() + "()");
        }
        return Optional.empty();
    }
}
