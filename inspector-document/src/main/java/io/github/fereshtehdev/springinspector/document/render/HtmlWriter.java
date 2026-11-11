package io.github.fereshtehdev.springinspector.document.render;

import io.github.fereshtehdev.springinspector.document.content.Block;
import io.github.fereshtehdev.springinspector.document.content.Block.Bullets;
import io.github.fereshtehdev.springinspector.document.content.Block.Figure;
import io.github.fereshtehdev.springinspector.document.content.Block.Heading;
import io.github.fereshtehdev.springinspector.document.content.Block.Paragraph;
import io.github.fereshtehdev.springinspector.document.content.Block.Table;
import io.github.fereshtehdev.springinspector.document.content.Document;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Arrow;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Box;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Placed;
import java.util.List;
import java.util.Locale;

/**
 * Writes a document as one HTML file that needs nothing else: styles and diagrams are inside it, it loads nothing
 * from the network and runs no script. It follows the reader's light or dark theme and prints cleanly.
 */
public final class HtmlWriter {

    private static final String STYLE = """
            :root { color-scheme: light dark; --text: #1c2430; --muted: #5b6676; --line: #d5dae2; --soft: #f3f5f8;
              --accent: #2f6f4f; --problem: #c8372d; --page: #ffffff; }
            @media (prefers-color-scheme: dark) { :root { --text: #e4e8ee; --muted: #9aa5b5; --line: #3a4352;
              --soft: #232a35; --accent: #7fc8a0; --problem: #ff7b6e; --page: #161b22; } }
            * { box-sizing: border-box; }
            body { margin: 0; background: var(--page); color: var(--text);
              font: 15px/1.55 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
            main { max-width: 1040px; margin: 0 auto; padding: 32px 16px 64px; }
            h1 { font-size: 28px; line-height: 1.2; margin: 0 0 4px; text-wrap: balance; }
            .subtitle { color: var(--muted); margin: 0 0 24px; }
            h2 { font-size: 20px; margin: 40px 0 12px; padding-top: 16px; border-top: 1px solid var(--line); }
            h3 { font-size: 16px; margin: 24px 0 8px; }
            p { margin: 8px 0; max-width: 78ch; }
            ul { margin: 8px 0; padding-left: 22px; max-width: 86ch; }
            li { margin: 4px 0; }
            nav { background: var(--soft); border-radius: 8px; padding: 12px 16px; margin: 0 0 8px; }
            nav ol { margin: 0; padding: 0; list-style: none; columns: 2 260px; }
            a { color: var(--accent); text-decoration: none; }
            a:hover { text-decoration: underline; }
            .scroll { overflow-x: auto; margin: 12px 0; }
            table { border-collapse: collapse; width: 100%; font-size: 14px; }
            th, td { text-align: left; vertical-align: top; padding: 6px 10px; border-bottom: 1px solid var(--line); }
            th { background: var(--soft); font-weight: 600; white-space: nowrap; }
            td.code { font: 13px/1.5 ui-monospace, "Cascadia Code", Consolas, monospace; overflow-wrap: anywhere; }
            figure { margin: 16px 0; }
            figcaption { color: var(--muted); font-size: 13px; margin-top: 6px; }
            svg { max-width: 100%; height: auto; }
            svg rect { fill: var(--soft); stroke: var(--muted); }
            svg text { fill: var(--text); font: 12px system-ui, sans-serif; text-anchor: middle; }
            svg text.detail, svg text.edge { fill: var(--muted); font-size: 10px; }
            svg text.edge { paint-order: stroke; stroke: var(--page); stroke-width: 4px; }
            svg path { fill: none; stroke: var(--muted); stroke-width: 1.2; }
            svg path.problem { stroke: var(--problem); stroke-width: 1.8; }
            svg polygon { fill: var(--muted); }
            svg polygon.problem { fill: var(--problem); }
            @media print { main { max-width: none; padding: 0; } nav { display: none; }
              h2 { break-after: avoid; } tr, figure { break-inside: avoid; } }
            """;

    private HtmlWriter() {}

    public static String write(Document document) {
        StringBuilder html = new StringBuilder(
                        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>")
                .append(escape(document.title()))
                .append("</title>\n<style>\n")
                .append(STYLE)
                .append("</style>\n</head>\n<body>\n<main>\n<h1>")
                .append(escape(document.title()))
                .append("</h1>\n<p class=\"subtitle\">")
                .append(escape(document.subtitle()))
                .append("</p>\n");
        contents(document.blocks(), html);
        int section = 0;
        for (Block block : document.blocks()) {
            switch (block) {
                case Heading heading
                when heading.level() == 1 ->
                    html.append("<h2 id=\"section-")
                            .append(++section)
                            .append("\">")
                            .append(escape(heading.text()))
                            .append("</h2>\n");
                case Heading heading ->
                    html.append("<h3>").append(escape(heading.text())).append("</h3>\n");
                case Paragraph paragraph ->
                    html.append("<p>").append(escape(paragraph.text())).append("</p>\n");
                case Bullets bullets -> {
                    html.append("<ul>\n");
                    bullets.items()
                            .forEach(item ->
                                    html.append("<li>").append(escape(item)).append("</li>\n"));
                    html.append("</ul>\n");
                }
                case Table table -> table(table, html);
                case Figure figure ->
                    html.append("<figure>\n")
                            .append(svg(DiagramLayout.of(figure.diagram()), figure.caption()))
                            .append("<figcaption>")
                            .append(escape(figure.caption()))
                            .append("</figcaption>\n</figure>\n");
            }
        }
        return html.append("</main>\n</body>\n</html>\n").toString();
    }

    private static void contents(List<Block> blocks, StringBuilder html) {
        html.append("<nav aria-label=\"Contents\">\n<ol>\n");
        int section = 0;
        for (Block block : blocks) {
            if (block instanceof Heading heading && heading.level() == 1) {
                html.append("<li><a href=\"#section-")
                        .append(++section)
                        .append("\">")
                        .append(escape(heading.text()))
                        .append("</a></li>\n");
            }
        }
        html.append("</ol>\n</nav>\n");
    }

    private static void table(Table table, StringBuilder html) {
        html.append("<div class=\"scroll\">\n<table>\n<thead><tr>");
        table.columns()
                .forEach(column -> html.append("<th scope=\"col\">")
                        .append(escape(column.title()))
                        .append("</th>"));
        html.append("</tr></thead>\n<tbody>\n");
        for (List<String> row : table.rows()) {
            html.append("<tr>");
            for (int at = 0; at < row.size(); at++) {
                html.append(table.columns().get(at).code() ? "<td class=\"code\">" : "<td>")
                        .append(escape(row.get(at)))
                        .append("</td>");
            }
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n</div>\n");
    }

    static String svg(Placed placed, String title) {
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" viewBox=\"0 0 ")
                .append(number(placed.width()))
                .append(' ')
                .append(number(placed.height()))
                .append("\" width=\"")
                .append(number(placed.width()))
                .append("\">\n<title>")
                .append(escape(title))
                .append("</title>\n");
        for (Arrow arrow : placed.arrows()) {
            String kind = arrow.problem() ? " class=\"problem\"" : "";
            svg.append("<path")
                    .append(kind)
                    .append(" d=\"M")
                    .append(point(arrow.x1(), arrow.y1()))
                    .append(" C")
                    .append(point(arrow.controlX1(), arrow.controlY1()))
                    .append(' ')
                    .append(point(arrow.controlX2(), arrow.controlY2()))
                    .append(' ')
                    .append(point(arrow.x2(), arrow.y2()))
                    .append("\"/>\n<polygon")
                    .append(kind)
                    .append(" points=\"");
            double[] head = ArrowHead.of(arrow);
            for (int at = 0; at < head.length; at += 2) {
                svg.append(point(head[at], head[at + 1])).append(' ');
            }
            svg.setLength(svg.length() - 1);
            svg.append("\"/>\n");
        }
        for (Box box : placed.boxes()) {
            svg.append("<rect x=\"")
                    .append(number(box.x()))
                    .append("\" y=\"")
                    .append(number(box.y()))
                    .append("\" width=\"")
                    .append(number(box.width()))
                    .append("\" height=\"")
                    .append(number(box.height()))
                    .append("\" rx=\"6\"/>\n");
            double baseline = box.detail().isEmpty() ? box.centerY() + 4 : box.centerY() - 3;
            text(svg, "", box.centerX(), baseline, box.label());
            if (!box.detail().isEmpty()) {
                text(svg, "detail", box.centerX(), box.centerY() + 12, box.detail());
            }
        }
        for (Arrow arrow : placed.arrows()) {
            if (!arrow.label().isEmpty()) {
                text(svg, "edge", arrow.labelX(), arrow.labelY() + 3, arrow.label());
            }
        }
        return svg.append("</svg>\n").toString();
    }

    private static void text(StringBuilder svg, String kind, double x, double y, String text) {
        svg.append("<text");
        if (!kind.isEmpty()) {
            svg.append(" class=\"").append(kind).append('"');
        }
        svg.append(" x=\"")
                .append(number(x))
                .append("\" y=\"")
                .append(number(y))
                .append("\">")
                .append(escape(text))
                .append("</text>\n");
    }

    private static String point(double x, double y) {
        return number(x) + "," + number(y);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** The inspected project's names and texts are data: nothing in them may become markup. */
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        String source = text;
        for (int at = 0; at < source.length(); at++) {
            char character = source.charAt(at);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
