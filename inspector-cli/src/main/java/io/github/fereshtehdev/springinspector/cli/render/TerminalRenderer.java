package io.github.fereshtehdev.springinspector.cli.render;

import io.github.fereshtehdev.springinspector.cli.render.Style.Ansi;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryResult;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import io.github.fereshtehdev.springinspector.core.domain.report.GateDecision;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.domain.report.SeverityCounts;
import io.github.fereshtehdev.springinspector.core.port.ReportRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The report card: one line per category, one line per finding, and the gate. With {@code --details} a category's
 * findings are shown in full: what is wrong, why it matters and how to fix it, with the patch.
 */
public final class TerminalRenderer implements ReportRenderer {

    private static final int TITLE_WIDTH = 13;
    private static final int SEVERITY_WIDTH = 8;
    private static final String DETAIL_INDENT = "    ";

    private final Style style;
    private final Optional<Category> details;

    public TerminalRenderer(Style style, Optional<Category> details) {
        this.style = style;
        this.details = details;
    }

    @Override
    public void render(ReportCard card, Appendable out) throws IOException {
        out.append(style.paint("Spring Inspector", Ansi.BOLD))
                .append("  score ")
                .append(style.paint(
                        card.score().value() + "/100",
                        Ansi.BOLD,
                        scoreColor(card.score().value())))
                .append("\n\n");
        if (card.categories().isEmpty()) {
            out.append("  No checks applied to the changed files.\n");
        }
        for (CategoryResult category : card.categories()) {
            renderCategory(category, out);
        }
        List<CategoryResult> withHidden = card.categories().stream()
                .filter(category -> category.hiddenFindings() > 0)
                .toList();
        for (CategoryResult category : withHidden) {
            out.append(style.symbol("  … ", "  ... "))
                    .append(String.valueOf(category.hiddenFindings()))
                    .append(" more in ")
                    .append(Categories.title(category.category()))
                    .append('\n');
        }
        for (CheckProblem problem : card.problems()) {
            out.append(style.paint("  " + style.symbol("⚠️ ", "! ") + Problems.describe(problem), Ansi.YELLOW))
                    .append('\n');
        }
        out.append('\n');
        renderGate(card.gate(), out);
        if (details.isPresent()) {
            Optional<CategoryResult> detailed = details.flatMap(card::category);
            if (detailed.isPresent()) {
                renderDetails(detailed.get(), out);
            } else {
                out.append("\n  No ").append(Categories.title(details.get())).append(" checks ran on these changes.\n");
            }
        } else if (card.categories().stream()
                .anyMatch(category -> !category.findings().isEmpty())) {
            out.append(style.paint(
                            "  Why and how to fix: add --details <category>, e.g. --details "
                                    + Categories.option(firstWithFindings(card)),
                            Ansi.DIM))
                    .append('\n');
        }
    }

    private void renderCategory(CategoryResult result, Appendable out) throws IOException {
        String symbol = switch (result.status()) {
            case PASSED -> style.symbol("✅", "[ OK ]");
            case FAILED -> style.symbol("❌", "[FAIL]");
            case HINTS -> style.symbol("💡", "[HINT]");
        };
        out.append("  ")
                .append(symbol)
                .append(' ')
                .append(style.paint(pad(Categories.title(result.category()), TITLE_WIDTH), Ansi.BOLD))
                .append(summary(result.counts()))
                .append('\n');
        for (Finding finding : result.findings()) {
            out.append("      ")
                    .append(style.paint(
                            pad(finding.severity().name(), SEVERITY_WIDTH), severityColors(finding.severity())))
                    .append(style.paint(finding.location().toString(), Ansi.CYAN))
                    .append("  ")
                    .append(finding.whatsWrong().description())
                    .append(finding.howToFix().patch().isPresent() ? style.paint("  [fix available]", Ansi.GREEN) : "")
                    .append('\n');
        }
    }

    private void renderGate(GateDecision gate, Appendable out) throws IOException {
        switch (gate) {
            case GateDecision.Pass pass ->
                out.append("  ")
                        .append(style.paint(style.symbol("🟢 ", "") + "Gate passed", Ansi.GREEN, Ansi.BOLD))
                        .append('\n');
            case GateDecision.Block(List<String> reasons) -> {
                out.append("  ")
                        .append(style.paint(style.symbol("⛔ ", "") + "Gate blocked", Ansi.RED, Ansi.BOLD))
                        .append('\n');
                for (String reason : reasons) {
                    out.append("     - ").append(reason).append('\n');
                }
            }
        }
    }

    private void renderDetails(CategoryResult result, Appendable out) throws IOException {
        out.append('\n')
                .append(style.paint(Categories.title(result.category()) + " in detail", Ansi.BOLD))
                .append('\n');
        if (result.findings().isEmpty()) {
            out.append("  Nothing to report.\n");
        }
        for (Finding finding : result.findings()) {
            out.append('\n')
                    .append(style.paint(
                            style.symbol("── ", "-- ") + finding.ruleId() + style.symbol(" · ", " - ")
                                    + finding.severity()
                                    + style.symbol(" · ", " - ") + "confidence "
                                    + finding.confidence().name().toLowerCase(Locale.ROOT),
                            severityColors(finding.severity())))
                    .append('\n')
                    .append(DETAIL_INDENT)
                    .append(style.paint(finding.location().toString(), Ansi.CYAN))
                    .append("\n\n");
            section(out, "What's wrong", finding.whatsWrong().description());
            for (String line : finding.whatsWrong().snippet().lines().toList()) {
                out.append(DETAIL_INDENT)
                        .append(style.paint(style.symbol("│ ", "| ") + line, Ansi.DIM))
                        .append('\n');
            }
            section(out, "Why it matters", finding.whyItMatters().explanation());
            for (Reference reference : finding.whyItMatters().references()) {
                out.append(DETAIL_INDENT)
                        .append(style.symbol("  ↳ ", "  -> "))
                        .append(reference.label())
                        .append(": ")
                        .append(reference.uri().toString())
                        .append('\n');
            }
            section(out, "How to fix", finding.howToFix().explanation());
            if (finding.howToFix().patch().isPresent()) {
                for (String line :
                        finding.howToFix().patch().get().unifiedDiff().lines().toList()) {
                    out.append(DETAIL_INDENT)
                            .append(style.paint(line, diffColor(line)))
                            .append('\n');
                }
            }
        }
    }

    private void section(Appendable out, String title, String text) throws IOException {
        out.append(DETAIL_INDENT).append(style.paint(title, Ansi.BOLD)).append('\n');
        out.append(DETAIL_INDENT).append(text).append('\n');
    }

    private static String summary(SeverityCounts counts) {
        List<String> parts = new ArrayList<>();
        addCount(parts, counts.blocker(), "blocker");
        addCount(parts, counts.major(), "major");
        addCount(parts, counts.minor(), "minor");
        addCount(parts, counts.info(), "info");
        return parts.isEmpty() ? "no findings" : String.join(", ", parts);
    }

    private static void addCount(List<String> parts, int count, String label) {
        if (count > 0) {
            parts.add(count + " " + label);
        }
    }

    private static Category firstWithFindings(ReportCard card) {
        return card.categories().stream()
                .filter(category -> !category.findings().isEmpty())
                .findFirst()
                .orElseThrow()
                .category();
    }

    private static Ansi scoreColor(int score) {
        if (score >= 90) {
            return Ansi.GREEN;
        }
        return score >= 70 ? Ansi.YELLOW : Ansi.RED;
    }

    private static Ansi[] severityColors(Severity severity) {
        return switch (severity) {
            case BLOCKER -> new Ansi[] {Ansi.RED, Ansi.BOLD};
            case MAJOR -> new Ansi[] {Ansi.RED};
            case MINOR -> new Ansi[] {Ansi.YELLOW};
            case INFO -> new Ansi[] {Ansi.BLUE};
        };
    }

    private static Ansi[] diffColor(String line) {
        if (line.startsWith("+") && !line.startsWith("+++")) {
            return new Ansi[] {Ansi.GREEN};
        }
        if (line.startsWith("-") && !line.startsWith("---")) {
            return new Ansi[] {Ansi.RED};
        }
        return line.startsWith("@@") ? new Ansi[] {Ansi.CYAN} : new Ansi[] {Ansi.DIM};
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text + " " : text + " ".repeat(width - text.length());
    }
}
