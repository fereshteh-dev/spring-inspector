package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tells strings built from runtime values apart from constant ones, from syntax alone: literals and
 * {@code UPPER_SNAKE_CASE} names count as constants, and a local variable stands for its initializer when nothing
 * else assigns it.
 */
public final class StringExpressions {

    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Set<String> FORMATTING_METHODS = Set.of("format", "formatted", "concat");
    private static final int MAX_LOCALS_FOLLOWED = 8;

    private StringExpressions() {}

    /** Whether the expression concatenates or formats at least one value that is not a constant. */
    public static boolean isBuiltFromValues(Expression expression) {
        return !runtimeValues(expression).isEmpty();
    }

    /** The non-constant parts of a built string; empty for constants and for a value passed on as it is. */
    public static List<Expression> runtimeValues(Expression expression) {
        Expression followed = followLocal(expression);
        List<Expression> values = new ArrayList<>();
        if (isBuilt(followed)) {
            collectRuntimeValues(followed, values);
        }
        return values;
    }

    /** The text of every string literal the expression is built from, joined by spaces. */
    public static String literalText(Expression expression) {
        List<String> parts = new ArrayList<>();
        followLocal(expression).walk(node -> {
            if (node instanceof StringLiteralExpr literal) {
                parts.add(literal.getValue());
            } else if (node instanceof TextBlockLiteralExpr block) {
                parts.add(block.getValue());
            }
            if (node instanceof NameExpr name && localInitializer(name).isPresent()) {
                parts.add(literalText(name));
            }
        });
        return String.join(" ", parts);
    }

    /** A local variable read is replaced by the expression that initialized it; anything else is returned as it is. */
    public static Expression followLocal(Expression expression) {
        Expression current = unwrap(expression);
        for (int followed = 0; followed < MAX_LOCALS_FOLLOWED; followed++) {
            Optional<Expression> initializer = localInitializer(current);
            if (initializer.isEmpty()) {
                return current;
            }
            current = unwrap(initializer.get());
        }
        return current;
    }

    private static void collectRuntimeValues(Expression expression, List<Expression> values) {
        Expression part = unwrap(expression);
        if (part instanceof BinaryExpr concatenation && isBuilt(concatenation)) {
            collectRuntimeValues(concatenation.getLeft(), values);
            collectRuntimeValues(concatenation.getRight(), values);
        } else if (part instanceof MethodCallExpr call && isBuilt(call)) {
            call.getScope()
                    .filter(scope -> !isStringClass(scope))
                    .ifPresent(scope -> collectRuntimeValues(scope, values));
            call.getArguments().forEach(argument -> collectRuntimeValues(argument, values));
        } else if (!isConstant(part)) {
            Expression followed = followLocal(part);
            boolean isLocal = localInitializer(part).isPresent();
            if (isLocal && isBuilt(followed)) {
                collectRuntimeValues(followed, values);
            } else if (!isLocal || !isConstant(followed)) {
                values.add(part);
            }
        }
    }

    private static boolean isBuilt(Expression expression) {
        if (expression instanceof BinaryExpr binary) {
            return binary.getOperator() == BinaryExpr.Operator.PLUS;
        }
        return expression instanceof MethodCallExpr call
                && FORMATTING_METHODS.contains(call.getNameAsString())
                && (!call.getNameAsString().equals("format")
                        || call.getScope()
                                .filter(StringExpressions::isStringClass)
                                .isPresent());
    }

    private static boolean isStringClass(Expression scope) {
        String name = scope.toString();
        return name.equals("String") || name.equals("java.lang.String");
    }

    private static boolean isConstant(Expression expression) {
        return expression instanceof LiteralExpr
                || (expression instanceof NameExpr name && isConstantName(name.getNameAsString()))
                || (expression instanceof FieldAccessExpr field && isConstantName(field.getNameAsString()));
    }

    private static boolean isConstantName(String name) {
        return CONSTANT_NAME.matcher(name).matches();
    }

    private static Expression unwrap(Expression expression) {
        Expression current = expression;
        while (current instanceof EnclosedExpr enclosed) {
            current = enclosed.getInner();
        }
        return current;
    }

    private static Optional<Expression> localInitializer(Expression expression) {
        if (!(expression instanceof NameExpr name)) {
            return Optional.empty();
        }
        Optional<Node> callable = enclosingCallable(expression);
        if (callable.isEmpty()) {
            return Optional.empty();
        }
        String variable = name.getNameAsString();
        List<VariableDeclarator> declarations = callable.get()
                .findAll(
                        VariableDeclarator.class,
                        declarator -> declarator.getNameAsString().equals(variable));
        boolean reassigned = callable.get()
                .findFirst(
                        AssignExpr.class,
                        assign -> assign.getTarget() instanceof NameExpr target
                                && target.getNameAsString().equals(variable))
                .isPresent();
        if (declarations.size() != 1 || reassigned) {
            return Optional.empty();
        }
        return declarations.getFirst().getInitializer();
    }

    private static Optional<Node> enclosingCallable(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent() && !(current.get() instanceof CallableDeclaration<?>)) {
            current = current.get().getParentNode();
        }
        return current;
    }
}
