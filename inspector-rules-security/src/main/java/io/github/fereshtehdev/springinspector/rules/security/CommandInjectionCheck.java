package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.StringExpressions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Operating system commands built from request input, through {@code Runtime.exec} or {@code ProcessBuilder}. */
public final class CommandInjectionCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.command-injection");

    private static final Set<String> LIST_FACTORIES = Set.of("of", "asList");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A caller who controls part of a command can run their own programs on the server, with all the rights of"
                    + " the application: read its secrets, change its data or move on to other machines.",
            List.of(
                    SecurityReferences.cwe(78, "OS command injection"),
                    SecurityReferences.owaspCheatSheet(
                            "OS Command Injection Defense", "OS_Command_Injection_Defense_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Use a Java library instead of a command where one exists. Otherwise use a fixed program with no shell,"
                    + " and pass only values checked against an allow-list as separate arguments.");

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
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class, CommandInjectionCheck::isRuntimeExec)) {
            report(file, call, call.getArguments(), findings);
        }
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            if (file.refersTo(creation.getType().getNameWithScope(), "java.lang.ProcessBuilder")) {
                report(file, creation, creation.getArguments(), findings);
            }
        }
        return findings;
    }

    private static boolean isRuntimeExec(MethodCallExpr call) {
        return call.getNameAsString().equals("exec")
                && call.getScope()
                        .map(StringExpressions::followLocal)
                        .filter(scope -> scope.toString().contains("getRuntime()"))
                        .isPresent();
    }

    private void report(JavaFile file, Node use, List<Expression> arguments, List<Finding> findings) {
        if (arguments.stream().anyMatch(argument -> carriesRequestInput(file, argument))) {
            findings.add(finding(
                    file,
                    use,
                    Severity.BLOCKER,
                    Confidence.HIGH,
                    "An operating system command is built from request input",
                    WHY,
                    FIX));
        }
    }

    private static boolean carriesRequestInput(JavaFile file, Expression argument) {
        Expression value = StringExpressions.followLocal(argument);
        if (value instanceof ArrayCreationExpr array && array.getInitializer().isPresent()) {
            return array.getInitializer().get().getValues().stream()
                    .anyMatch(element -> carriesRequestInput(file, element));
        }
        if (value instanceof ArrayInitializerExpr initializer) {
            return initializer.getValues().stream().anyMatch(element -> carriesRequestInput(file, element));
        }
        if (value instanceof MethodCallExpr call && LIST_FACTORIES.contains(call.getNameAsString())) {
            return call.getArguments().stream().anyMatch(element -> carriesRequestInput(file, element));
        }
        return RequestInputs.isRequestInput(file, value);
    }
}
