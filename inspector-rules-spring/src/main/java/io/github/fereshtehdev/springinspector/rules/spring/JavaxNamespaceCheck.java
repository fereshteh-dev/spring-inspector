package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.ImportDeclaration;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code javax.*} imports of Jakarta EE APIs in a Spring Boot 3 or later project, which uses {@code jakarta.*}. Only
 * packages that moved are reported: {@code javax.annotation.Nullable} and the JDK's own {@code javax} packages stay.
 */
public final class JavaxNamespaceCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("migration.javax-namespace");

    private static final List<String> MOVED_PACKAGES = List.of(
            "javax.persistence.",
            "javax.servlet.",
            "javax.validation.",
            "javax.transaction.",
            "javax.inject.",
            "javax.mail.",
            "javax.ws.rs.",
            "javax.websocket.",
            "javax.json.");
    private static final Set<String> MOVED_ANNOTATIONS =
            Set.of("javax.annotation.PostConstruct", "javax.annotation.PreDestroy", "javax.annotation.Resource");
    private static final Set<String> SILENTLY_IGNORED =
            Set.of("javax.annotation.PostConstruct", "javax.annotation.PreDestroy");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Spring Boot 3 moved to Jakarta EE 9+, where these APIs live under jakarta.*. Spring no longer reads the"
                    + " javax annotations, so @PostConstruct and @PreDestroy methods stop running without any error,"
                    + " and other javax types either fail to compile or come from outdated jars.",
            List.of(SpringCode.springDocs("Spring Boot 3.0 Migration Guide", "spring-boot/upgrading.html")));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.MIGRATION;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringSpringBoot(VersionRange.atLeast(Version.of(3, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file.unit().getImports().stream()
                .flatMap(declaration -> jakartaName(declaration).stream()
                        .map(jakarta -> finding(
                                file,
                                declaration,
                                SILENTLY_IGNORED.contains(declaration.getNameAsString())
                                        ? Severity.MAJOR
                                        : Severity.MINOR,
                                Confidence.HIGH,
                                declaration.getNameAsString() + " is the pre-Jakarta name of " + jakarta,
                                WHY,
                                HowToFix.withPatch(
                                        "Import " + jakarta
                                                + " and depend on the jakarta artifact Spring Boot manages.",
                                        file.patch(List.of(file.replace(declaration.getName(), jakarta)))))))
                .toList();
    }

    private static Optional<String> jakartaName(ImportDeclaration declaration) {
        String name = declaration.getNameAsString();
        boolean moved = MOVED_PACKAGES.stream().anyMatch(name::startsWith)
                || MOVED_ANNOTATIONS.contains(name)
                || (declaration.isAsterisk()
                        && MOVED_PACKAGES.stream().anyMatch(prefix -> (name + ".").equals(prefix)));
        return moved ? Optional.of("jakarta." + name.substring("javax.".length())) : Optional.empty();
    }
}
