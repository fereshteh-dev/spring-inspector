package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * File paths built from request input without checking where they lead. A method that normalizes the path and checks
 * it with {@code startsWith} is taken as validated.
 */
public final class PathTraversalCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.path-traversal");

    private static final Set<String> FILE_TYPES = Set.of(
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.FileReader",
            "java.io.FileWriter");
    private static final Set<String> PATH_FACTORIES = Set.of("java.nio.file.Path", "java.nio.file.Paths");

    private static final WhyItMatters WHY = new WhyItMatters(
            "A request value such as ../../etc/passwd or an absolute path escapes the intended directory, so the"
                    + " caller can read or overwrite any file the application can reach.",
            List.of(
                    SecurityReferences.cwe(22, "Path traversal"),
                    SecurityReferences.owaspCheatSheet("File Upload", "File_Upload_Cheat_Sheet")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Resolve the name against a fixed base directory, normalize it, and reject it unless it still starts with"
                    + " the base: Path file = base.resolve(name).normalize(); if (!file.startsWith(base)) throw ...;"
                    + " Better still, map request values to files you choose, such as generated ids.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (ObjectCreationExpr creation : file.unit().findAll(ObjectCreationExpr.class)) {
            String type = creation.getType().getNameWithScope();
            if (FILE_TYPES.stream().anyMatch(fileType -> file.refersTo(type, fileType))) {
                check(file, creation, creation.getArguments()).ifPresent(findings::add);
            }
        }
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            boolean pathFactory = (call.getNameAsString().equals("of")
                            || call.getNameAsString().equals("get"))
                    && call.getScope()
                            .map(scope -> PATH_FACTORIES.stream()
                                    .anyMatch(factory -> file.refersTo(scope.toString(), factory)))
                            .orElse(false);
            boolean resolve = call.getNameAsString().equals("resolve")
                    || call.getNameAsString().equals("resolveSibling");
            if (pathFactory || resolve) {
                check(file, call, call.getArguments()).ifPresent(findings::add);
            }
        }
        return findings;
    }

    private Optional<Finding> check(JavaFile file, Node use, List<Expression> arguments) {
        boolean fromRequest = arguments.stream().anyMatch(argument -> RequestInputs.isRequestInput(file, argument));
        if (!fromRequest || isValidated(use)) {
            return Optional.empty();
        }
        return Optional.of(finding(
                file, use, Severity.MAJOR, Confidence.HIGH, "A file path is built from request input", WHY, FIX));
    }

    private static boolean isValidated(Node use) {
        String method =
                Nodes.ancestor(use, MethodDeclaration.class).map(Node::toString).orElse("");
        return method.contains("normalize()") && method.contains("startsWith(");
    }
}
