package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
import java.util.Set;
import java.util.regex.Pattern;

/** Passwords, tokens and keys passed to a logger or printed to the console. */
public final class SecretLoggedCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.secret-logged");

    private static final Set<String> LOG_METHODS = Set.of("trace", "debug", "info", "warn", "error", "fatal", "log");
    private static final Pattern LOGGER = Pattern.compile("(?i)(log|logger|[a-z]*log)");
    private static final Set<String> PRINT_METHODS = Set.of("println", "print", "printf");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Logs are copied to files, aggregators and support tickets and kept for months, readable by far more"
                    + " people than the secret was meant for.",
            List.of(
                    SecurityReferences.cwe(532, "Insertion of sensitive information into log file"),
                    SecurityReferences.owaspCheatSheet("Logging", "Logging_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Leave the secret out of the message. Log that it was present or which key was used, never its value.");

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
        return file.unit().findAll(MethodCallExpr.class, SecretLoggedCheck::writesLog).stream()
                .flatMap(call -> secretArgument(call).stream()
                        .map(name -> finding(
                                file,
                                call,
                                Severity.MAJOR,
                                Confidence.HIGH,
                                name + " is written to the log",
                                WHY,
                                FIX)))
                .toList();
    }

    private static boolean writesLog(MethodCallExpr call) {
        Optional<String> scope = call.getScope().map(Expression::toString);
        if (scope.isEmpty()) {
            return false;
        }
        boolean logger = LOG_METHODS.contains(call.getNameAsString())
                && LOGGER.matcher(scope.get()).matches();
        boolean console = PRINT_METHODS.contains(call.getNameAsString())
                && (scope.get().equals("System.out") || scope.get().equals("System.err"));
        return logger || console;
    }

    private static Optional<String> secretArgument(MethodCallExpr call) {
        return call.getArguments().stream()
                .flatMap(argument -> argument.findAll(Expression.class, SecretLoggedCheck::namesSecret).stream())
                .map(Expression::toString)
                .findFirst();
    }

    private static boolean namesSecret(Expression expression) {
        String name = switch (expression) {
            case NameExpr variable -> variable.getNameAsString();
            case FieldAccessExpr field -> field.getNameAsString();
            case MethodCallExpr getter
            when getter.getArguments().isEmpty() && getter.getNameAsString().startsWith("get") ->
                getter.getNameAsString().substring(3);
            default -> "";
        };
        return !name.isEmpty() && Secrets.isSensitiveName(name) && !name.matches("[A-Z0-9_]+");
    }
}
