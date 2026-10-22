package io.github.fereshtehdev.springinspector.intellij.ui;

import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView;
import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView.Hotspot;
import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView.Part;
import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView.Style;
import io.github.fereshtehdev.springinspector.intellij.report.ArchitectureView.Suggestion;
import java.util.stream.Collectors;

/** The Architecture tab's HTML: styles with their evidence, parts, cycles, overloaded classes and suggestions. */
final class ArchitectureHtml {

    private ArchitectureHtml() {}

    static String of(ArchitectureView view) {
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h2>Architecture of ")
                .append(escape(view.rootPackage().isEmpty() ? "the project" : view.rootPackage()))
                .append("</h2><p>")
                .append(view.classes())
                .append(" classes</p>");
        html.append("<h3>Styles</h3>");
        if (view.styles().isEmpty()) {
            html.append("<p>No common style recognised.</p>");
        } else {
            html.append("<ul>");
            for (Style style : view.styles()) {
                html.append("<li><b>")
                        .append(escape(style.title()))
                        .append("</b> (")
                        .append(escape(style.confidence()))
                        .append(" confidence): ")
                        .append(escape(style.description()))
                        .append("<br><i>")
                        .append(escape(String.join("; ", style.evidence())))
                        .append("</i></li>");
            }
            html.append("</ul>");
        }
        html.append("<h3>Parts</h3><ul>");
        for (Part part : view.parts()) {
            html.append("<li><b>")
                    .append(escape(part.name()))
                    .append("</b>, ")
                    .append(part.classes())
                    .append(part.classes() == 1 ? " class" : " classes");
            if (!part.dependsOn().isEmpty()) {
                html.append(", uses ")
                        .append(escape(part.dependsOn().entrySet().stream()
                                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                                .collect(Collectors.joining(", "))));
            }
            html.append("</li>");
        }
        html.append("</ul>");
        if (!view.cycles().isEmpty()) {
            html.append("<h3>Cycles</h3><ul>");
            view.cycles().forEach(cycle -> html.append("<li>").append(escape(String.join(" ⇄ ", cycle))).append("</li>"));
            html.append("</ul>");
        }
        if (!view.hotspots().isEmpty()) {
            html.append("<h3>Classes that do too much</h3><ul>");
            for (Hotspot hotspot : view.hotspots()) {
                html.append("<li>")
                        .append(escape(hotspot.type()))
                        .append(": used by ")
                        .append(hotspot.usedBy())
                        .append(", uses ")
                        .append(hotspot.uses())
                        .append("</li>");
            }
            html.append("</ul>");
        }
        html.append("<h3>Suggestions</h3>");
        if (view.suggestions().isEmpty()) {
            html.append("<p>None: the structure is consistent.</p>");
        }
        for (Suggestion suggestion : view.suggestions()) {
            html.append("<p><b>")
                    .append(escape(suggestion.title()))
                    .append("</b><br>")
                    .append(escape(suggestion.why()))
                    .append("</p><ul>");
            suggestion.steps().forEach(step -> html.append("<li>").append(escape(step.strip())).append("</li>"));
            html.append("</ul>");
        }
        return html.append("</body></html>").toString();
    }

    private static String escape(String text) {
        return FindingHtml.escape(text);
    }
}
