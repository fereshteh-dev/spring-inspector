package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code return null} from a method declared to return {@code Optional} or a collection. The patch returns the empty
 * value of that type instead.
 */
public final class ReturnNullCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.return-null");

    private static final Map<String, String> EMPTY_VALUES = Map.of(
            "java.util.Optional", "Optional.empty()",
            "java.util.List", "List.of()",
            "java.util.Collection", "List.of()",
            "java.util.Set", "Set.of()",
            "java.util.Map", "Map.of()");

    private static final WhyItMatters OPTIONAL = new WhyItMatters(
            "Optional exists so callers never see null. Returning null from it breaks every caller that does"
                    + " result.isPresent() or result.map(...), with a NullPointerException far from the cause.",
            List.of());

    private static final WhyItMatters COLLECTION = new WhyItMatters(
            "Callers iterate collections without checking for null. A null instead of an empty collection turns a"
                    + " normal \"nothing found\" into a NullPointerException.",
            List.of());

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.CLEAN_CODE;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (MethodDeclaration method : file.unit().findAll(MethodDeclaration.class)) {
            Optional<Map.Entry<String, String>> type = returnType(file, method);
            if (type.isEmpty()) {
                continue;
            }
            for (ReturnStmt statement : method.findAll(ReturnStmt.class, ReturnNullCheck::returnsNull)) {
                if (!belongsTo(statement, method)) {
                    continue;
                }
                boolean optional = type.get().getKey().equals("java.util.Optional");
                String empty = type.get().getValue();
                List<TextEdit> edits = new ArrayList<>();
                edits.add(file.replace(statement.getExpression().orElseThrow(), empty));
                file.addImport(
                                type.get().getKey().equals("java.util.Collection")
                                        ? "java.util.List"
                                        : type.get().getKey())
                        .ifPresent(edits::add);
                findings.add(finding(
                        file,
                        statement,
                        optional ? Severity.MAJOR : Severity.MINOR,
                        Confidence.HIGH,
                        method.getNameAsString() + " returns null instead of " + empty,
                        optional ? OPTIONAL : COLLECTION,
                        HowToFix.withPatch("Return " + empty + ".", file.patch(edits))));
            }
        }
        return findings;
    }

    private static Optional<Map.Entry<String, String>> returnType(JavaFile file, MethodDeclaration method) {
        if (!method.getType().isClassOrInterfaceType()) {
            return Optional.empty();
        }
        String name = method.getType().asClassOrInterfaceType().getNameWithScope();
        return EMPTY_VALUES.entrySet().stream()
                .filter(entry -> file.refersTo(name, entry.getKey()))
                .findFirst();
    }

    private static boolean returnsNull(ReturnStmt statement) {
        return statement
                .getExpression()
                .filter(NullLiteralExpr.class::isInstance)
                .isPresent();
    }

    /** A return inside a lambda in the method returns from the lambda, not from the method. */
    private static boolean belongsTo(ReturnStmt statement, MethodDeclaration method) {
        Optional<Node> current = statement.getParentNode();
        while (current.isPresent() && !current.get().equals(method)) {
            if (current.get() instanceof LambdaExpr) {
                return false;
            }
            current = current.get().getParentNode();
        }
        return true;
    }
}
