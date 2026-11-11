package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads annotations by their simple name, which is enough to describe a project and needs no class path. */
final class Annotations {

    private Annotations() {}

    static Optional<AnnotationExpr> find(NodeWithAnnotations<?> node, String... simpleNames) {
        Set<String> wanted = Set.of(simpleNames);
        return node.getAnnotations().stream()
                .filter(annotation -> wanted.contains(annotation.getName().getIdentifier()))
                .findFirst();
    }

    static boolean has(NodeWithAnnotations<?> node, String... simpleNames) {
        return find(node, simpleNames).isPresent();
    }

    /** The values of the first of {@code members} the annotation sets; {@code value} is also its unnamed member. */
    static List<String> values(AnnotationExpr annotation, String... members) {
        for (String member : members) {
            Optional<Expression> expression = member(annotation, member);
            if (expression.isPresent()) {
                return texts(expression.get());
            }
        }
        return List.of();
    }

    static Optional<String> value(AnnotationExpr annotation, String... members) {
        return values(annotation, members).stream().findFirst();
    }

    static Optional<Expression> member(AnnotationExpr annotation, String member) {
        return switch (annotation) {
            case SingleMemberAnnotationExpr single ->
                member.equals("value") ? Optional.of(single.getMemberValue()) : Optional.empty();
            case NormalAnnotationExpr normal ->
                normal.getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals(member))
                        .map(MemberValuePair::getValue)
                        .findFirst();
            default -> Optional.empty();
        };
    }

    static List<String> texts(Expression expression) {
        if (expression instanceof ArrayInitializerExpr array) {
            List<String> texts = new ArrayList<>();
            array.getValues().forEach(value -> texts.add(text(value)));
            return texts;
        }
        return List.of(text(expression));
    }

    /** The text an expression stands for; a constant whose value is elsewhere is shown by name in braces. */
    static String text(Expression expression) {
        return switch (expression) {
            case StringLiteralExpr string -> string.asString();
            case TextBlockLiteralExpr block -> block.asString().strip();
            case LiteralStringValueExpr literal -> literal.getValue();
            case LiteralExpr literal -> literal.toString();
            case ClassExpr type -> type.getType().asString();
            case BinaryExpr binary
            when binary.getOperator() == BinaryExpr.Operator.PLUS -> text(binary.getLeft()) + text(binary.getRight());
            case FieldAccessExpr constant -> "{" + constant.getNameAsString() + "}";
            default -> "{" + expression + "}";
        };
    }
}
