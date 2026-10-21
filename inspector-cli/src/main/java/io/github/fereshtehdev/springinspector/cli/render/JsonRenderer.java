package io.github.fereshtehdev.springinspector.cli.render;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.report.CategoryResult;
import io.github.fereshtehdev.springinspector.core.domain.report.CheckProblem;
import io.github.fereshtehdev.springinspector.core.domain.report.GateDecision;
import io.github.fereshtehdev.springinspector.core.domain.report.ReportCard;
import io.github.fereshtehdev.springinspector.core.port.ReportRenderer;
import java.io.IOException;
import java.util.List;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** The report card as JSON, with an explicit schema rather than serialized domain objects, so it stays stable. */
public final class JsonRenderer implements ReportRenderer {

    static final JsonMapper MAPPER =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final String toolVersion;

    public JsonRenderer(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    @Override
    public void render(ReportCard card, Appendable out) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.putObject("tool").put("name", "spring-inspector").put("version", toolVersion);
        root.put("score", card.score().value());
        ObjectNode gate = root.putObject("gate");
        gate.put("blocks", card.gate().blocks());
        ArrayNode reasons = gate.putArray("reasons");
        if (card.gate() instanceof GateDecision.Block(List<String> blockReasons)) {
            blockReasons.forEach(reasons::add);
        }
        ArrayNode categories = root.putArray("categories");
        for (CategoryResult result : card.categories()) {
            ObjectNode category = categories.addObject();
            category.put("category", Categories.option(result.category()));
            category.put("status", result.status().name());
            category.putObject("counts")
                    .put("blocker", result.counts().blocker())
                    .put("major", result.counts().major())
                    .put("minor", result.counts().minor())
                    .put("info", result.counts().info());
            category.put("hiddenFindings", result.hiddenFindings());
            ArrayNode findings = category.putArray("findings");
            result.findings().forEach(finding -> finding(findings.addObject(), finding));
        }
        ArrayNode problems = root.putArray("problems");
        for (CheckProblem problem : card.problems()) {
            ObjectNode node =
                    problems.addObject().put("ruleId", problem.ruleId().value());
            switch (problem) {
                case CheckProblem.Failed(var ruleId, String reason) ->
                    node.put("kind", "failed").put("reason", reason);
                case CheckProblem.TimedOut(var ruleId, var timeout) ->
                    node.put("kind", "timedOut").put("timeoutMillis", timeout.toMillis());
            }
        }
        out.append(MAPPER.writeValueAsString(root)).append('\n');
    }

    private static void finding(ObjectNode node, Finding finding) {
        node.put("ruleId", finding.ruleId().value())
                .put("severity", finding.severity().name())
                .put("confidence", finding.confidence().name())
                .put("source", finding.source().name())
                .put("file", finding.location().file().toString().replace('\\', '/'))
                .put("startLine", finding.location().lines().start())
                .put("endLine", finding.location().lines().end())
                .put("whatsWrong", finding.whatsWrong().description())
                .put("snippet", finding.whatsWrong().snippet())
                .put("whyItMatters", finding.whyItMatters().explanation());
        ArrayNode references = node.putArray("references");
        for (Reference reference : finding.whyItMatters().references()) {
            references
                    .addObject()
                    .put("label", reference.label())
                    .put("url", reference.uri().toString());
        }
        node.put("howToFix", finding.howToFix().explanation());
        finding.howToFix()
                .patch()
                .ifPresentOrElse(patch -> node.put("patch", patch.unifiedDiff()), () -> node.putNull("patch"));
    }
}
