package io.github.fereshtehdev.springinspector.javamodel;

import com.github.javaparser.ast.Node;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.change.FileChange;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.util.ArrayList;
import java.util.List;

/**
 * A check that looks at one Java file at a time. Its result for a file must depend only on that file, the types it
 * resolves through {@link JavaFile}, and the project's detected versions: that is what the finding cache keys on.
 */
public abstract class JavaCheck implements Check {

    @Override
    public final List<Finding> inspect(InspectionInput input) {
        JavaModel model = JavaModel.of(input);
        List<Finding> findings = new ArrayList<>();
        for (FileChange change : input.changeSet().files()) {
            if (change.path().toString().endsWith(".java")) {
                findings.addAll(model.findings(this, change.path()));
            }
        }
        return List.copyOf(findings);
    }

    protected abstract List<Finding> inspect(JavaFile file);

    protected final Finding finding(
            JavaFile file,
            Node node,
            Severity severity,
            Confidence confidence,
            String whatsWrong,
            WhyItMatters whyItMatters,
            HowToFix howToFix) {
        return finding(file, node, file.snippet(node), severity, confidence, whatsWrong, whyItMatters, howToFix);
    }

    /** A finding on the node's lines that shows a shorter snippet, such as only a method's signature. */
    protected final Finding finding(
            JavaFile file,
            Node node,
            String snippet,
            Severity severity,
            Confidence confidence,
            String whatsWrong,
            WhyItMatters whyItMatters,
            HowToFix howToFix) {
        return new Finding(
                id(),
                category(),
                severity,
                file.location(node),
                new WhatsWrong(whatsWrong, snippet),
                whyItMatters,
                howToFix,
                confidence,
                FindingSource.RULE);
    }
}
