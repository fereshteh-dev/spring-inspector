package io.github.fereshtehdev.springinspector.cli.render;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryResult;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.ReportRenderer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** SARIF 2.1.0, as GitHub code scanning and other tools import it. Paths are relative to the source root. */
public final class SarifRenderer implements ReportRenderer {

    static final String SCHEMA = "https://json.schemastore.org/sarif-2.1.0.json";
    static final String SOURCE_ROOT = "%SRCROOT%";
    private static final String INFORMATION_URI = "https://github.com/fereshteh-dev/spring-inspector";

    private final String toolVersion;

    public SarifRenderer(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    @Override
    public void render(ReportCard card, Appendable out) throws IOException {
        List<Finding> findings = new ArrayList<>();
        card.categories().stream().map(CategoryResult::findings).forEach(findings::addAll);
        card.suppressed().forEach(suppressed -> findings.add(suppressed.finding()));
        Map<RuleId, Integer> ruleIndex = new LinkedHashMap<>();
        findings.forEach(finding -> ruleIndex.putIfAbsent(finding.ruleId(), ruleIndex.size()));

        ObjectNode root = JsonRenderer.MAPPER.createObjectNode();
        root.put("$schema", SCHEMA).put("version", "2.1.0");
        ObjectNode run = root.putArray("runs").addObject();
        ObjectNode driver = run.putObject("tool").putObject("driver");
        driver.put("name", "Spring Inspector")
                .put("semanticVersion", toolVersion)
                .put("informationUri", INFORMATION_URI);
        ArrayNode rules = driver.putArray("rules");
        for (RuleId ruleId : ruleIndex.keySet()) {
            rule(rules.addObject(), ruleId, findings);
        }
        ArrayNode results = run.putArray("results");
        int suppressedFrom = findings.size() - card.suppressed().size();
        for (int i = 0; i < findings.size(); i++) {
            Finding finding = findings.get(i);
            ObjectNode result = results.addObject();
            result(result, finding, Objects.requireNonNull(ruleIndex.get(finding.ruleId())));
            if (i >= suppressedFrom) {
                result.putArray("suppressions")
                        .addObject()
                        .put("kind", "inSource")
                        .put(
                                "justification",
                                card.suppressed().get(i - suppressedFrom).reason());
            }
        }
        ObjectNode invocation = run.putArray("invocations").addObject();
        invocation.put("executionSuccessful", card.problems().isEmpty());
        ArrayNode notifications = invocation.putArray("toolExecutionNotifications");
        for (Location comment : card.suppressionsWithoutReason()) {
            notifications
                    .addObject()
                    .put("level", "warning")
                    .putObject("message")
                    .put("text", "inspector:ignore at " + comment + " was not applied: it needs reason=\"...\"");
        }
        for (CheckProblem problem : card.problems()) {
            notifications
                    .addObject()
                    .put("level", "warning")
                    .putObject("message")
                    .put("text", Problems.describe(problem));
        }
        out.append(JsonRenderer.MAPPER.writeValueAsString(root)).append('\n');
    }

    private static void rule(ObjectNode rule, RuleId ruleId, List<Finding> findings) {
        List<Finding> ofRule = findings.stream()
                .filter(finding -> finding.ruleId().equals(ruleId))
                .toList();
        Finding first = ofRule.getFirst();
        rule.put("id", ruleId.value()).put("name", ruleId.value());
        rule.putObject("shortDescription").put("text", ruleId.value());
        rule.putObject("fullDescription").put("text", first.whyItMatters().explanation());
        rule.putObject("help").put("text", first.howToFix().explanation());
        if (!first.whyItMatters().references().isEmpty()) {
            rule.put(
                    "helpUri",
                    first.whyItMatters().references().getFirst().uri().toString());
        }
        ObjectNode properties = rule.putObject("properties");
        properties.putArray("tags").add(Categories.option(first.category()));
        if (first.category() == Category.SECURITY) {
            Severity highest =
                    ofRule.stream().map(Finding::severity).min(Enum::compareTo).orElseThrow();
            properties.put("security-severity", securitySeverity(highest));
        }
    }

    private static void result(ObjectNode result, Finding finding, int ruleIndex) {
        result.put("ruleId", finding.ruleId().value())
                .put("ruleIndex", ruleIndex)
                .put("level", level(finding.severity()));
        result.putObject("message").put("text", finding.whatsWrong().description());
        ObjectNode location = result.putArray("locations").addObject().putObject("physicalLocation");
        location.putObject("artifactLocation")
                .put("uri", finding.location().file().toString().replace('\\', '/'))
                .put("uriBaseId", SOURCE_ROOT);
        location.putObject("region")
                .put("startLine", finding.location().lines().start())
                .put("endLine", finding.location().lines().end());
        ObjectNode properties = result.putObject("properties")
                .put("severity", finding.severity().name())
                .put("confidence", finding.confidence().name())
                .put("howToFix", finding.howToFix().explanation());
        finding.howToFix().patch().ifPresent(patch -> properties.put("patch", patch.unifiedDiff()));
    }

    private static String level(Severity severity) {
        return switch (severity) {
            case BLOCKER, MAJOR -> "error";
            case MINOR -> "warning";
            case INFO -> "note";
        };
    }

    /** GitHub maps 9.0+ to critical, 7.0+ to high, 4.0+ to medium and lower to low. */
    private static String securitySeverity(Severity severity) {
        return switch (severity) {
            case BLOCKER -> "9.0";
            case MAJOR -> "7.0";
            case MINOR -> "4.0";
            case INFO -> "1.0";
        };
    }
}
