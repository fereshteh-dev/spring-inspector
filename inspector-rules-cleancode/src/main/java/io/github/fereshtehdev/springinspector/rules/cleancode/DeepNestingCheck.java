package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Control statements nested deeper than {@code max-depth} (default {@value #DEFAULT_MAX_DEPTH}) inside one method. An
 * {@code else if} continues its chain and does not add a level. Each method is reported once, at its first statement
 * past the limit.
 */
public final class DeepNestingCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.deep-nesting");
    static final int DEFAULT_MAX_DEPTH = 4;

    private static final WhyItMatters WHY = new WhyItMatters(
            "Every level of nesting adds a condition the reader must keep in mind. Deeply nested code hides which"
                    + " branch runs when, and is where bugs in rarely taken paths survive.",
            List.of());

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Return early for the special cases (guard clauses), extract the inner blocks into well-named methods, or"
                    + " replace condition chains with a switch or polymorphism.");

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
        int maxDepth = file.options().positiveInteger("max-depth").orElse(DEFAULT_MAX_DEPTH);
        List<Finding> findings = new ArrayList<>();
        for (CallableDeclaration<?> callable : file.unit().findAll(CallableDeclaration.class)) {
            callable
                    .findAll(
                            Statement.class,
                            statement -> isLevel(statement)
                                    && enclosingCallable(statement)
                                            .filter(callable::equals)
                                            .isPresent()
                                    && depth(statement) > maxDepth)
                    .stream()
                    .findFirst()
                    .ifPresent(deepest -> findings.add(finding(
                            file,
                            deepest,
                            file.source()
                                    .snippet(LineRange.line(file.lines(deepest).start())),
                            Severity.MINOR,
                            Confidence.HIGH,
                            callable.getNameAsString() + " nests " + depth(deepest) + " levels deep; the limit is "
                                    + maxDepth,
                            WHY,
                            FIX)));
        }
        return findings;
    }

    private static int depth(Node statement) {
        int depth = 0;
        Node current = statement;
        while (current != null && !(current instanceof CallableDeclaration<?>)) {
            if (isLevel(current)) {
                depth++;
            }
            current = current.getParentNode().orElse(null);
        }
        return depth;
    }

    private static boolean isLevel(Node node) {
        if (node instanceof IfStmt ifStatement) {
            return !isElseIf(ifStatement);
        }
        return node instanceof ForStmt
                || node instanceof ForEachStmt
                || node instanceof WhileStmt
                || node instanceof DoStmt
                || node instanceof SwitchStmt
                || node instanceof TryStmt
                || node instanceof SynchronizedStmt;
    }

    private static boolean isElseIf(IfStmt ifStatement) {
        return ifStatement
                .getParentNode()
                .filter(parent -> parent instanceof IfStmt outer
                        && outer.getElseStmt().filter(ifStatement::equals).isPresent())
                .isPresent();
    }

    private static Optional<CallableDeclaration<?>> enclosingCallable(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent() && !(current.get() instanceof CallableDeclaration<?>)) {
            current = current.get().getParentNode();
        }
        return current.map(found -> (CallableDeclaration<?>) found);
    }
}
