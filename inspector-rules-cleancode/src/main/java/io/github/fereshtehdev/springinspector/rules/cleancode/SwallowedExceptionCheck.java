package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.UnionType;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.List;

/**
 * Empty catch blocks. A comment inside, or a parameter named {@code _}, {@code ignored} or {@code expected}, marks the
 * silence as deliberate. A swallowed {@link InterruptedException} is worse and gets a fix that restores the flag.
 */
public final class SwallowedExceptionCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.swallowed-exception");

    private static final String INTERRUPTED = "InterruptedException";

    private static final WhyItMatters SWALLOWED = new WhyItMatters(
            "The failure disappears without a trace: the program carries on in a state nobody expected, and the"
                    + " log that would explain the eventual symptom is missing.",
            List.of(new Reference(
                    "CWE-390: Detection of error condition without action",
                    URI.create("https://cwe.mitre.org/data/definitions/390.html"))));

    private static final WhyItMatters INTERRUPT_LOST = new WhyItMatters(
            "Catching InterruptedException clears the thread's interrupt flag. Without restoring it, executors and"
                    + " shutdown hooks cannot stop the thread, so shutdowns hang and cancelled work keeps running.",
            List.of(new Reference(
                    "Java Concurrency: dealing with InterruptedException",
                    URI.create("https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/"
                            + "InterruptedException.html"))));

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
        return file.unit().findAll(CatchClause.class, SwallowedExceptionCheck::isSwallowed).stream()
                .map(clause -> report(file, clause))
                .toList();
    }

    private static boolean isSwallowed(CatchClause clause) {
        BlockStmt body = clause.getBody();
        String name = clause.getParameter().getNameAsString();
        boolean deliberate = name.equals("_") || name.startsWith("ignored") || name.startsWith("expected");
        return body.getStatements().isEmpty() && body.getAllContainedComments().isEmpty() && !deliberate;
    }

    private Finding report(JavaFile file, CatchClause clause) {
        boolean interrupted = !(clause.getParameter().getType() instanceof UnionType)
                && clause.getParameter().getType().asString().endsWith(INTERRUPTED);
        if (!interrupted) {
            return finding(
                    file,
                    clause,
                    Severity.MINOR,
                    Confidence.HIGH,
                    "The caught " + clause.getParameter().getType() + " is ignored",
                    SWALLOWED,
                    HowToFix.withoutPatch("Handle the exception, rethrow it wrapped in a more specific exception, or"
                            + " at least log it. If ignoring it is correct, name the parameter `ignored` and say why"
                            + " in a comment."));
        }
        String indent = file.indentation(clause);
        String unit = indent.contains("\t") ? "\t" : "    ";
        String separator = file.lineSeparator();
        String body =
                "{" + separator + indent + unit + "Thread.currentThread().interrupt();" + separator + indent + "}";
        return finding(
                file,
                clause,
                Severity.MAJOR,
                Confidence.HIGH,
                "InterruptedException is caught and ignored, which clears the thread's interrupt flag",
                INTERRUPT_LOST,
                HowToFix.withPatch(
                        "Restore the flag with Thread.currentThread().interrupt(), then stop the current work.",
                        file.patch(List.of(file.replace(clause.getBody(), body)))));
    }
}
