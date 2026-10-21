package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code java.util.Random}, {@code Math.random()} or {@code ThreadLocalRandom} producing values named like tokens,
 * passwords, codes or salts. A {@code new Random()} gets a patch to {@code SecureRandom}, which extends it.
 */
public final class InsecureRandomCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.insecure-random");

    private static final Pattern SECURITY_PURPOSE = Pattern.compile(
            ".*(token|password|secret|otp|nonce|salt|session|verification|reset|apikey|csrf|activation|captcha).*");

    private static final WhyItMatters WHY = new WhyItMatters(
            "java.util.Random is predictable: after seeing a few outputs, or knowing roughly when it was seeded, an"
                    + " attacker can compute the next values and guess the tokens or codes made from them.",
            List.of(
                    SecurityReferences.cwe(338, "Use of cryptographically weak PRNG"),
                    SecurityReferences.owaspCheatSheet("Cryptographic Storage", "Cryptographic_Storage_Cheat_Sheet")));

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
            if (file.refersTo(creation.getType().getNameWithScope(), "java.util.Random")
                    && hasSecurityPurpose(creation)) {
                findings.add(
                        report(file, creation, "java.util.Random", Optional.of(secureRandomPatch(file, creation))));
            }
        }
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            Optional<String> scope = call.getScope().map(Expression::toString);
            boolean mathRandom = call.getNameAsString().equals("random")
                    && scope.map(name -> file.refersTo(name, "java.lang.Math")).orElse(false);
            boolean threadLocal = call.getNameAsString().equals("current")
                    && scope.map(name -> file.refersTo(name, "java.util.concurrent.ThreadLocalRandom"))
                            .orElse(false);
            if ((mathRandom || threadLocal) && hasSecurityPurpose(call)) {
                findings.add(report(file, call, mathRandom ? "Math.random()" : "ThreadLocalRandom", Optional.empty()));
            }
        }
        return findings;
    }

    private Finding report(JavaFile file, Node use, String generator, Optional<Patch> patch) {
        String explanation = "Use java.security.SecureRandom for anything an attacker must not guess.";
        return finding(
                file,
                use,
                Severity.MAJOR,
                Confidence.HIGH,
                generator + " generates a value used for security",
                WHY,
                patch.map(diff -> HowToFix.withPatch(explanation, diff))
                        .orElseGet(() -> HowToFix.withoutPatch(explanation)));
    }

    /** The variable, field or method the random value ends up in says what it is for. */
    private static boolean hasSecurityPurpose(Node use) {
        List<String> names = new ArrayList<>();
        Optional<Node> current = use.getParentNode();
        while (current.isPresent()) {
            switch (current.get()) {
                case VariableDeclarator variable -> names.add(variable.getNameAsString());
                case AssignExpr assign -> names.add(assign.getTarget().toString());
                case CallableDeclaration<?> callable -> names.add(callable.getNameAsString());
                default -> {}
            }
            if (current.get() instanceof CallableDeclaration<?>) {
                break;
            }
            current = current.get().getParentNode();
        }
        return names.stream()
                .map(name -> name.toLowerCase(Locale.ROOT).replace("_", ""))
                .anyMatch(name -> SECURITY_PURPOSE.matcher(name).matches());
    }

    private static Patch secureRandomPatch(JavaFile file, ObjectCreationExpr creation) {
        List<TextEdit> edits = new ArrayList<>();
        edits.add(file.replace(creation, "new SecureRandom()"));
        boolean imported = file.unit().getImports().stream()
                .anyMatch(declaration -> declaration.getNameAsString().equals("java.security.SecureRandom")
                        || (declaration.isAsterisk()
                                && declaration.getNameAsString().equals("java.security")));
        if (!imported) {
            edits.add(
                    file.unit().getImports().isEmpty()
                            ? file.unit()
                                    .getPackageDeclaration()
                                    .map(declaration -> file.insertAfterLine(
                                            declaration,
                                            file.lineSeparator() + "import java.security.SecureRandom;"
                                                    + file.lineSeparator()))
                                    .orElseGet(() -> TextEdit.insert(
                                            0, "import java.security.SecureRandom;" + file.lineSeparator()))
                            : file.insertBefore(
                                    file.unit().getImports().get(0),
                                    "import java.security.SecureRandom;" + file.lineSeparator()));
        }
        return file.patch(edits);
    }
}
