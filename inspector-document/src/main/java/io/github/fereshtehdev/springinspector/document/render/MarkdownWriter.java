package io.github.fereshtehdev.springinspector.document.render;

import io.github.fereshtehdev.springinspector.document.content.Block;
import io.github.fereshtehdev.springinspector.document.content.Block.Bullets;
import io.github.fereshtehdev.springinspector.document.content.Block.Column;
import io.github.fereshtehdev.springinspector.document.content.Block.Figure;
import io.github.fereshtehdev.springinspector.document.content.Block.Heading;
import io.github.fereshtehdev.springinspector.document.content.Block.Paragraph;
import io.github.fereshtehdev.springinspector.document.content.Block.Table;
import io.github.fereshtehdev.springinspector.document.content.Diagram;
import io.github.fereshtehdev.springinspector.document.content.Document;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a document as Markdown, with diagrams as Mermaid flowcharts that GitHub, GitLab and IDEs draw. */
public final class MarkdownWriter {

    private MarkdownWriter() {}

    public static String write(Document document) {
        StringBuilder markdown = new StringBuilder("# ")
                .append(text(document.title()))
                .append("\n\n_")
                .append(text(document.subtitle()))
                .append("_\n");
        for (Block block : document.blocks()) {
            markdown.append('\n');
            switch (block) {
                case Heading heading ->
                    markdown.append(heading.level() == 1 ? "## " : "### ")
                            .append(text(heading.text()))
                            .append('\n');
                case Paragraph paragraph ->
                    markdown.append(text(paragraph.text())).append('\n');
                case Bullets bullets ->
                    bullets.items()
                            .forEach(item ->
                                    markdown.append("- ").append(text(item)).append('\n'));
                case Table table -> table(table, markdown);
                case Figure figure -> figure(figure, markdown);
            }
        }
        return markdown.toString();
    }

    private static void table(Table table, StringBuilder markdown) {
        markdown.append('|');
        table.columns()
                .forEach(column ->
                        markdown.append(' ').append(text(column.title())).append(" |"));
        markdown.append("\n|");
        table.columns().forEach(column -> markdown.append("---|"));
        markdown.append('\n');
        for (List<String> row : table.rows()) {
            markdown.append('|');
            for (int at = 0; at < row.size(); at++) {
                markdown.append(' ')
                        .append(cell(table.columns().get(at), row.get(at)))
                        .append(" |");
            }
            markdown.append('\n');
        }
    }

    private static String cell(Column column, String value) {
        if (value.isEmpty() || !column.code()) {
            return text(value);
        }
        return "`" + oneLine(value).replace('`', '\'').replace("|", "\\|") + "`";
    }

    private static void figure(Figure figure, StringBuilder markdown) {
        Diagram diagram = figure.diagram();
        markdown.append("```mermaid\nflowchart LR\n");
        Map<String, String> ids = new LinkedHashMap<>();
        for (Diagram.Node node : diagram.nodes()) {
            String id = "n" + ids.size();
            if (ids.putIfAbsent(node.id(), id) != null) {
                continue;
            }
            markdown.append("    ").append(id).append("[\"").append(label(node.label()));
            if (!node.detail().isEmpty()) {
                markdown.append("<br/>").append(label(node.detail()));
            }
            markdown.append("\"]\n");
        }
        List<String> problems = new ArrayList<>();
        int link = 0;
        for (Diagram.Edge edge : diagram.edges()) {
            String from = ids.get(edge.from());
            String to = ids.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            markdown.append("    ").append(from).append(" -->");
            if (!edge.label().isEmpty()) {
                markdown.append("|\"").append(label(edge.label())).append("\"|");
            }
            markdown.append(' ').append(to).append('\n');
            if (edge.problem()) {
                problems.add(String.valueOf(link));
            }
            link++;
        }
        if (!problems.isEmpty()) {
            markdown.append("    linkStyle ")
                    .append(String.join(",", problems))
                    .append(" stroke:#d33,stroke-width:2px\n");
        }
        markdown.append("```\n\n_").append(text(figure.caption())).append("_\n");
    }

    /** Text inside a Mermaid label, where quotes and markup would end the label or be drawn as HTML. */
    private static String label(String text) {
        return oneLine(text)
                .replace("&", "#amp;")
                .replace("\"", "#quot;")
                .replace("<", "#lt;")
                .replace(">", "#gt;")
                .replace("`", "'");
    }

    /** Plain text as Markdown shows it: characters that would start formatting, a link or HTML are escaped. */
    static String text(String text) {
        StringBuilder escaped = new StringBuilder();
        String source = oneLine(text);
        for (int at = 0; at < source.length(); at++) {
            char character = source.charAt(at);
            switch (character) {
                case '\\', '`', '*', '_', '[', ']', '|', '#' ->
                    escaped.append('\\').append(character);
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '&' -> escaped.append("&amp;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
