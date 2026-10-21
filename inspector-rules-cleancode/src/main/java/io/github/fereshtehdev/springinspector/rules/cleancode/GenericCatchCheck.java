package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.UnionType;
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
import java.util.Set;

/**
 * {@code catch (Exception e)}, {@code Throwable} or {@code RuntimeException} that handles the failure and goes on
 * without rethrowing. Empty catch blocks are the swallowed-exception rule's to report.
 */
public final class GenericCatchCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.generic-catch");

    private static final Set<String> GENERIC = Set.of("Exception", "Throwable", "RuntimeException");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A catch-all handles failures nobody anticipated, such as NullPointerException or a broken invariant, the"
                    + " same way as the expected ones, so bugs are logged and skipped instead of surfacing.",
            List.of());

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Catch the specific exceptions this code can recover from and let the rest propagate to a central"
                    + " handler such as a @ControllerAdvice.");

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
        return file.unit().findAll(CatchClause.class, GenericCatchCheck::isGenericHandler).stream()
                .map(clause -> finding(
                        file,
                        clause.getParameter(),
                        Severity.MINOR,
                        Confidence.MEDIUM,
                        "catch (" + clause.getParameter().getType() + ") handles every failure the same way",
                        WHY,
                        FIX))
                .toList();
    }

    private static boolean isGenericHandler(CatchClause clause) {
        boolean generic = !(clause.getParameter().getType() instanceof UnionType)
                && GENERIC.contains(clause.getParameter().getType().asString().replace("java.lang.", ""));
        boolean handles = !clause.getBody().getStatements().isEmpty()
                && clause.getBody().findAll(ThrowStmt.class).isEmpty();
        return generic && handles;
    }
}
