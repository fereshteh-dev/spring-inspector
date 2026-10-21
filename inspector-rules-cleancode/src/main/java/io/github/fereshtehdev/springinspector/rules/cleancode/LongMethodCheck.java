package io.github.fereshtehdev.springinspector.rules.cleancode;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
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
 * Methods and constructors whose body has more lines of code than {@code max-lines} (default
 * {@value #DEFAULT_MAX_BODY_LINES}), blank lines and lines holding only comments excluded.
 */
public final class LongMethodCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("cleancode.long-method");
    static final int DEFAULT_MAX_BODY_LINES = 60;
    static final String MAX_LINES = "max-lines";

    private static final WhyItMatters WHY = new WhyItMatters(
            "A long method does several things at once. It is hard to name, hard to test in parts, and every change"
                    + " risks breaking a part the author was not looking at.",
            List.of());

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Extract the steps into well-named private methods, or move a cohesive part into its own class.");

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
        int maxBodyLines = file.options().positiveInteger(MAX_LINES).orElse(DEFAULT_MAX_BODY_LINES);
        List<Finding> findings = new ArrayList<>();
        for (CallableDeclaration<?> callable : file.unit().findAll(CallableDeclaration.class)) {
            body(callable).ifPresent(body -> {
                int lines = codeLines(file, body);
                if (lines > maxBodyLines) {
                    findings.add(finding(
                            file,
                            callable,
                            file.source()
                                    .snippet(LineRange.line(
                                            file.lines(callable.getName()).start())),
                            Severity.MINOR,
                            Confidence.HIGH,
                            callable.getNameAsString() + " has " + lines + " lines of code; the limit is "
                                    + maxBodyLines,
                            WHY,
                            FIX));
                }
            });
        }
        return findings;
    }

    private static Optional<BlockStmt> body(CallableDeclaration<?> callable) {
        return switch (callable) {
            case MethodDeclaration method -> method.getBody();
            case ConstructorDeclaration constructor -> Optional.of(constructor.getBody());
            default -> Optional.empty();
        };
    }

    private static int codeLines(JavaFile file, BlockStmt body) {
        LineRange range = file.lines(body);
        String[] lines = file.source().snippet(range).split("\\R", -1);
        int code = 0;
        boolean inBlockComment = false;
        for (int i = 1; i < lines.length - 1; i++) {
            String line = lines[i].strip();
            if (inBlockComment) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (line.startsWith("/*")) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            code++;
        }
        return code;
    }
}
