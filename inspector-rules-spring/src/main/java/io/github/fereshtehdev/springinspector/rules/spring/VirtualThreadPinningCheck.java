package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Blocking I/O inside {@code synchronized} on Java 21 to 23, where it pins a virtual thread to its carrier. Java 24
 * removed the pinning (JEP 491), so newer projects are not inspected.
 */
public final class VirtualThreadPinningCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.virtual-thread-pinning");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Before Java 24, a virtual thread that blocks inside synchronized cannot unmount from its carrier thread."
                    + " With spring.threads.virtual.enabled, a few such calls occupy all carriers and the whole"
                    + " application stops making progress.",
            List.of(new Reference(
                    "JEP 491: Synchronize virtual threads without pinning",
                    URI.create("https://openjdk.org/jeps/491"))));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Guard the section with a java.util.concurrent.locks.ReentrantLock instead of synchronized, or move the"
                    + " blocking call out of it. Upgrading to Java 24 or later also removes the problem.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_BOOT;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringJava(VersionRange.between(Version.of(21, 0, 0), Version.of(24, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Node> sections = new ArrayList<>(file.unit().findAll(SynchronizedStmt.class));
        sections.addAll(file.unit().findAll(MethodDeclaration.class, MethodDeclaration::isSynchronized));
        List<Finding> findings = new ArrayList<>();
        for (Node section : sections) {
            for (MethodCallExpr call : section.findAll(MethodCallExpr.class)) {
                BlockingCalls.describe(file, call)
                        .ifPresent(blocking -> findings.add(finding(
                                file,
                                call,
                                Severity.MINOR,
                                Confidence.MEDIUM,
                                blocking + " blocks inside synchronized and pins virtual threads",
                                WHY,
                                FIX)));
            }
        }
        return findings;
    }
}
