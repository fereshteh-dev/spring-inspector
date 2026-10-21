package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Private fields and methods that nothing in the file refers to. Members with annotations, which frameworks may use
 * by reflection, and Java serialization hooks are left alone. The patch deletes the member and its comment.
 */
public final class UnusedPrivateCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.unused-private");

    private static final Set<String> SERIALIZATION =
            Set.of("serialVersionUID", "readObject", "writeObject", "readResolve", "writeReplace", "readObjectNoData");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Dead code still has to be read, compiled and kept compiling. Readers assume it matters and wonder what"
                    + " calls it; deleting it loses nothing, since version control remembers it.",
            List.of());

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
        Set<String> referenced = referencedNames(file);
        List<Finding> findings = new ArrayList<>();
        for (FieldDeclaration field : file.unit().findAll(FieldDeclaration.class, UnusedPrivateCheck::isCandidate)) {
            if (field.getVariables().size() == 1
                    && !referenced.contains(field.getVariable(0).getNameAsString())) {
                findings.add(report(
                        file, field, "Private field " + field.getVariable(0).getNameAsString()));
            }
        }
        for (MethodDeclaration method : file.unit().findAll(MethodDeclaration.class, UnusedPrivateCheck::isCandidate)) {
            if (!referenced.contains(method.getNameAsString())) {
                findings.add(report(file, method, "Private method " + method.getNameAsString()));
            }
        }
        return findings;
    }

    private Finding report(JavaFile file, BodyDeclaration<?> member, String what) {
        List<TextEdit> edits = new ArrayList<>();
        member.getComment().ifPresent(comment -> edits.add(file.delete(comment)));
        edits.add(file.delete(member));
        return finding(
                file,
                member,
                file.source().snippet(LineRange.line(file.lines(member).start())),
                Severity.MINOR,
                Confidence.HIGH,
                what + " is never used",
                WHY,
                HowToFix.withPatch("Delete it.", file.patch(edits)));
    }

    private static boolean isCandidate(BodyDeclaration<?> member) {
        String name = switch (member) {
            case FieldDeclaration field ->
                field.isPrivate() ? field.getVariable(0).getNameAsString() : "";
            case MethodDeclaration method -> method.isPrivate() ? method.getNameAsString() : "";
            default -> "";
        };
        return !name.isEmpty() && member.getAnnotations().isEmpty() && !SERIALIZATION.contains(name);
    }

    private static Set<String> referencedNames(JavaFile file) {
        Set<String> names = new HashSet<>();
        file.unit().walk(node -> referencedName(node, names));
        return names;
    }

    private static void referencedName(Node node, Set<String> names) {
        switch (node) {
            case NameExpr name -> names.add(name.getNameAsString());
            case FieldAccessExpr access -> names.add(access.getNameAsString());
            case MethodCallExpr call -> names.add(call.getNameAsString());
            case MethodReferenceExpr reference -> names.add(reference.getIdentifier());
            default -> {}
        }
    }
}
