package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * An {@code if / else if} chain of three or more {@code instanceof} tests on the same value, which a pattern-matching
 * {@code switch} expresses directly. Java 21 and later.
 */
public final class InstanceofChainCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.instanceof-chain");
    private static final int MIN_BRANCHES = 3;

    private static final WhyItMatters WHY = new WhyItMatters(
            "A switch over the value's type names each case once, binds the typed variable without a cast, and with a"
                    + " sealed type lets the compiler report a missing case instead of silently falling through.",
            List.of(new Reference("JEP 441: Pattern Matching for switch", URI.create("https://openjdk.org/jeps/441"))));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.CLEAN_CODE;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringJava(VersionRange.atLeast(Version.of(21, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file.unit().findAll(IfStmt.class, InstanceofChainCheck::startsChain).stream()
                .flatMap(chain -> testedValue(chain).stream()
                        .filter(value -> branchesTesting(chain, value) >= MIN_BRANCHES)
                        .map(value -> finding(
                                file,
                                chain.getCondition(),
                                Severity.INFO,
                                Confidence.HIGH,
                                "An if/else chain tests the type of " + value + " " + branchesTesting(chain, value)
                                        + " times",
                                WHY,
                                HowToFix.withoutPatch("Use switch (" + value + ") { case Type t -> ... } with one case"
                                        + " per type."))))
                .toList();
    }

    private static boolean startsChain(IfStmt statement) {
        return statement
                .getParentNode()
                .filter(parent -> parent instanceof IfStmt outer
                        && outer.getElseStmt().filter(statement::equals).isPresent())
                .isEmpty();
    }

    private static Optional<String> testedValue(IfStmt statement) {
        return statement.getCondition() instanceof InstanceOfExpr test
                ? Optional.of(test.getExpression().toString())
                : Optional.empty();
    }

    private static int branchesTesting(IfStmt chain, String value) {
        int branches = 0;
        Optional<Statement> current = Optional.of(chain);
        while (current.isPresent() && current.get() instanceof IfStmt branch) {
            Expression condition = branch.getCondition();
            if (!(condition instanceof InstanceOfExpr test)
                    || !test.getExpression().toString().equals(value)) {
                break;
            }
            branches++;
            current = branch.getElseStmt();
        }
        return branches;
    }
}
