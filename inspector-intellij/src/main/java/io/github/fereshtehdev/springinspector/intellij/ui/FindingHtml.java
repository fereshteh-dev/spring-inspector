package io.github.fereshtehdev.springinspector.intellij.ui;

import io.github.fereshtehdev.springinspector.intellij.report.Report.Finding;
import io.github.fereshtehdev.springinspector.intellij.report.Report.Reference;
import java.util.List;
import java.util.Locale;

/**
 * The details panel's HTML for one finding: what is wrong, why it matters, how to fix it, and the patch. Snippets and
 * texts come from the inspected project, so everything is escaped, and only http(s) references become links.
 */
final class FindingHtml {

    private FindingHtml() {}

    static String of(Finding finding) {
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<p><b>")
                .append(escape(finding.severity().name()))
                .append("</b> &middot; ")
                .append(escape(finding.ruleId()))
                .append(" &middot; confidence ")
                .append(escape(finding.confidence().toLowerCase(Locale.ROOT)))
                .append("<br>")
                .append(escape(finding.location()))
                .append("</p>");
        section(html, "What's wrong", paragraphs(finding.whatsWrong()));
        if (!finding.snippet().isBlank()) {
            html.append("<pre>").append(escape(finding.snippet())).append("</pre>");
        }
        section(html, "Why it matters", paragraphs(finding.whyItMatters()) + references(finding.references()));
        section(html, "How to fix", paragraphs(finding.howToFix()));
        finding.patch().ifPresent(patch -> section(html, "Patch", "<pre>" + escape(patch) + "</pre>"));
        return html.append("</body></html>").toString();
    }

    static String message(String text) {
        return "<html><body>" + paragraphs(text) + "</body></html>";
    }

    private static void section(StringBuilder html, String title, String body) {
        html.append("<h3>").append(title).append("</h3>").append(body);
    }

    /** Lines starting with "- " become a list; other lines stay paragraphs. */
    private static String paragraphs(String text) {
        StringBuilder html = new StringBuilder();
        boolean inList = false;
        for (String line : text.split("\n", -1)) {
            boolean item = line.startsWith("- ");
            if (item && !inList) {
                html.append("<ul>");
            } else if (!item && inList) {
                html.append("</ul>");
            }
            inList = item;
            if (item) {
                html.append("<li>").append(escape(line.substring(2))).append("</li>");
            } else if (!line.isBlank()) {
                html.append("<p>").append(escape(line)).append("</p>");
            }
        }
        return inList ? html.append("</ul>").toString() : html.toString();
    }

    private static String references(List<Reference> references) {
        StringBuilder html = new StringBuilder();
        for (Reference reference : references) {
            String url = reference.url();
            boolean web = url.startsWith("https://") || url.startsWith("http://");
            html.append("<p>&rarr; ");
            if (web) {
                html.append("<a href=\"")
                        .append(escape(url))
                        .append("\">")
                        .append(escape(reference.label()))
                        .append("</a>");
            } else {
                html.append(escape(reference.label())).append(": ").append(escape(url));
            }
            html.append("</p>");
        }
        return html.toString();
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '&' -> escaped.append("&amp;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
