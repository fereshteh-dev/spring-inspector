package io.github.fereshtehdev.springinspector.document.render;

import io.github.fereshtehdev.springinspector.document.content.Block;
import io.github.fereshtehdev.springinspector.document.content.Block.Bullets;
import io.github.fereshtehdev.springinspector.document.content.Block.Column;
import io.github.fereshtehdev.springinspector.document.content.Block.Figure;
import io.github.fereshtehdev.springinspector.document.content.Block.Heading;
import io.github.fereshtehdev.springinspector.document.content.Block.Paragraph;
import io.github.fereshtehdev.springinspector.document.content.Block.Table;
import io.github.fereshtehdev.springinspector.document.content.Document;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Arrow;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Box;
import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Placed;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

/**
 * Writes a document as an A4 PDF with page numbers and a bookmark for every section. It uses the fonts every PDF
 * reader has, which cover Latin text; other characters are written as {@code ?}, so use HTML for those.
 */
public final class PdfWriter {

    private static final float MARGIN = 48;
    private static final float BODY = 10;
    private static final float CELL = 8;
    private static final float CELL_PADDING = 3;
    private static final int CELL_LINES = 40;
    private static final float[] TEXT = {0.11f, 0.14f, 0.19f};
    private static final float[] MUTED = {0.36f, 0.40f, 0.46f};
    private static final float[] LINE = {0.80f, 0.83f, 0.87f};
    private static final float[] SOFT = {0.95f, 0.96f, 0.97f};
    private static final float[] ACCENT = {0.18f, 0.44f, 0.31f};
    private static final float[] PROBLEM = {0.78f, 0.22f, 0.18f};
    private static final float[] WHITE = {1, 1, 1};

    private final PDDocument pdf;
    private final PDFont regular = new PDType1Font(FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(FontName.HELVETICA_BOLD);
    private final PDFont italic = new PDType1Font(FontName.HELVETICA_OBLIQUE);
    private final PDFont mono = new PDType1Font(FontName.COURIER);
    private final PDDocumentOutline outline = new PDDocumentOutline();
    private final float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private final float bottom = MARGIN + 14;
    private PDPage page;
    private PDPageContentStream stream;
    /** The baseline area's upper edge: what is drawn next goes below it. */
    private float y;

    private PdfWriter(PDDocument pdf) throws IOException {
        this.pdf = pdf;
        this.page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        this.stream = new PDPageContentStream(pdf, page);
        this.y = PDRectangle.A4.getHeight() - MARGIN;
    }

    public static byte[] write(Document document) {
        try (PDDocument pdf = new PDDocument()) {
            PdfWriter writer = new PdfWriter(pdf);
            writer.document(document);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pdf.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void document(Document document) throws IOException {
        PDDocumentInformation information = pdf.getDocumentInformation();
        information.setTitle(printable(document.title()));
        information.setCreator("Spring Inspector");
        pdf.getDocumentCatalog().setDocumentOutline(outline);
        lines(wrap(document.title(), bold, 20, width), bold, 20, 25, TEXT, 0);
        y -= 2;
        lines(wrap(document.subtitle(), regular, 9, width), regular, 9, 12, MUTED, 0);
        y -= 6;
        rule();
        List<Block> blocks = document.blocks();
        for (int at = 0; at < blocks.size(); at++) {
            Block block = blocks.get(at);
            switch (block) {
                case Heading heading -> {
                    keepWithFigure(blocks, at);
                    heading(heading);
                }
                case Paragraph paragraph -> {
                    y -= 4;
                    lines(wrap(paragraph.text(), regular, BODY, width), regular, BODY, 13.5f, TEXT, 0);
                }
                case Bullets bullets -> bullets(bullets);
                case Table table -> table(table);
                case Figure figure -> figure(figure);
            }
        }
        stream.close();
        footers(document.title());
    }

    /** A heading, its introduction and the figure they announce start on the same page when a page can hold them. */
    private void keepWithFigure(List<Block> blocks, int heading) throws IOException {
        for (int at = heading + 1; at < Math.min(blocks.size(), heading + 3); at++) {
            if (blocks.get(at) instanceof Figure figure) {
                float needed = figureHeight(DiagramLayout.of(figure.diagram())) + 110;
                if (needed < PDRectangle.A4.getHeight() - 2 * MARGIN) {
                    ensure(needed);
                }
                return;
            }
        }
    }

    private float figureHeight(Placed placed) {
        float room = PDRectangle.A4.getHeight() - 2 * MARGIN - 60;
        float scale = (float) Math.min(1, Math.min(width / placed.width(), room / placed.height()));
        return (float) placed.height() * scale;
    }

    private void heading(Heading heading) throws IOException {
        boolean section = heading.level() == 1;
        float size = section ? 14 : 11;
        // A heading alone at the foot of a page reads badly: keep room for what follows.
        ensure(section ? 90 : 60);
        y -= section ? 16 : 10;
        if (section) {
            PDPageXYZDestination destination = new PDPageXYZDestination();
            destination.setPage(page);
            destination.setLeft((int) MARGIN);
            destination.setTop((int) (y + 4));
            PDOutlineItem bookmark = new PDOutlineItem();
            bookmark.setTitle(printable(heading.text()));
            bookmark.setDestination(destination);
            outline.addLast(bookmark);
        }
        lines(wrap(heading.text(), bold, size, width), bold, size, size + 4, section ? ACCENT : TEXT, 0);
        y -= 2;
    }

    private void bullets(Bullets bullets) throws IOException {
        y -= 3;
        for (String item : bullets.items()) {
            List<String> wrapped = wrap(item, regular, BODY, width - 14);
            ensure(13.5f);
            text("•", regular, BODY, MARGIN + 3, y - BODY, TEXT);
            lines(wrapped, regular, BODY, 13.5f, TEXT, 14);
            y -= 1.5f;
        }
    }

    private void table(Table table) throws IOException {
        float[] widths = columnWidths(table);
        List<String> titles = table.columns().stream().map(Column::title).toList();
        y -= 6;
        ensure(60);
        row(table.columns(), titles, widths, true);
        for (List<String> cells : table.rows()) {
            if (!row(table.columns(), cells, widths, false)) {
                newPage();
                row(table.columns(), titles, widths, true);
                row(table.columns(), cells, widths, false);
            }
        }
        y -= 4;
    }

    /**
     * Every column gets room for its longest word, so titles and short values are not cut in the middle, and the
     * rest of the width is shared by weight. Code is left out of this: class names and paths may be cut.
     */
    private float[] columnWidths(Table table) throws IOException {
        int columns = table.columns().size();
        float[] needed = new float[columns];
        int total = 0;
        for (int at = 0; at < columns; at++) {
            Column column = table.columns().get(at);
            total += Math.max(1, column.weight());
            float longest = longestWord(column.title(), bold);
            if (!column.code()) {
                for (List<String> row : table.rows()) {
                    longest = Math.max(longest, longestWord(row.get(at), regular));
                }
            }
            needed[at] = Math.min(longest + 2 * CELL_PADDING + 1, width / 4);
        }
        float spare = width;
        for (float minimum : needed) {
            spare -= minimum;
        }
        float[] widths = new float[columns];
        for (int at = 0; at < columns; at++) {
            float share = (float) Math.max(1, table.columns().get(at).weight()) / total;
            widths[at] = spare >= 0 ? needed[at] + spare * share : width * share;
        }
        return widths;
    }

    private static float longestWord(String text, PDFont font) throws IOException {
        float longest = 0;
        for (String word : printable(text).split(" ", -1)) {
            longest = Math.max(longest, stringWidth(word, font, CELL));
        }
        return longest;
    }

    /** Draws one table row, unless it does not fit on the page: then nothing is drawn and the answer is no. */
    private boolean row(List<Column> columns, List<String> cells, float[] widths, boolean header) throws IOException {
        float leading = CELL + 2.4f;
        List<List<String>> wrapped = new ArrayList<>();
        int tallest = 1;
        for (int at = 0; at < cells.size(); at++) {
            PDFont font = font(columns.get(at), header);
            List<String> cell = wrap(cells.get(at), font, size(columns.get(at), header), widths[at] - 2 * CELL_PADDING);
            if (cell.size() > CELL_LINES) {
                cell = new ArrayList<>(cell.subList(0, CELL_LINES));
                cell.add("...");
            }
            wrapped.add(cell);
            tallest = Math.max(tallest, cell.size());
        }
        float height = tallest * leading + 2 * CELL_PADDING;
        if (!header && y - height < bottom) {
            return false;
        }
        if (header) {
            fill(MARGIN, y - height, width, height, SOFT);
        }
        float x = MARGIN;
        for (int at = 0; at < wrapped.size(); at++) {
            float baseline = y - CELL_PADDING - CELL;
            for (String line : wrapped.get(at)) {
                text(
                        line,
                        font(columns.get(at), header),
                        size(columns.get(at), header),
                        x + CELL_PADDING,
                        baseline,
                        TEXT);
                baseline -= leading;
            }
            x += widths[at];
        }
        y -= height;
        stream.setStrokingColor(LINE[0], LINE[1], LINE[2]);
        stream.setLineWidth(0.5f);
        stream.moveTo(MARGIN, y);
        stream.lineTo(MARGIN + width, y);
        stream.stroke();
        return true;
    }

    private PDFont font(Column column, boolean header) {
        return header ? bold : column.code() ? mono : regular;
    }

    private static float size(Column column, boolean header) {
        return !header && column.code() ? CELL - 0.8f : CELL;
    }

    private void figure(Figure figure) throws IOException {
        Placed placed = DiagramLayout.of(figure.diagram());
        float height = figureHeight(placed);
        float scale = height / (float) placed.height();
        y -= 8;
        ensure(height + 24);
        float left = MARGIN + (width - (float) placed.width() * scale) / 2;
        float top = y;
        for (Arrow arrow : placed.arrows()) {
            float[] color = arrow.problem() ? PROBLEM : MUTED;
            stream.setStrokingColor(color[0], color[1], color[2]);
            stream.setLineWidth((arrow.problem() ? 1.4f : 0.9f) * scale);
            stream.moveTo(left + (float) arrow.x1() * scale, top - (float) arrow.y1() * scale);
            stream.curveTo(
                    left + (float) arrow.controlX1() * scale,
                    top - (float) arrow.controlY1() * scale,
                    left + (float) arrow.controlX2() * scale,
                    top - (float) arrow.controlY2() * scale,
                    left + (float) arrow.x2() * scale,
                    top - (float) arrow.y2() * scale);
            stream.stroke();
            double[] head = ArrowHead.of(arrow);
            stream.setNonStrokingColor(color[0], color[1], color[2]);
            stream.moveTo(left + (float) head[0] * scale, top - (float) head[1] * scale);
            stream.lineTo(left + (float) head[2] * scale, top - (float) head[3] * scale);
            stream.lineTo(left + (float) head[4] * scale, top - (float) head[5] * scale);
            stream.closePath();
            stream.fill();
        }
        for (Box box : placed.boxes()) {
            float x = left + (float) box.x() * scale;
            float boxTop = top - (float) box.y() * scale;
            float boxWidth = (float) box.width() * scale;
            float boxHeight = (float) box.height() * scale;
            fill(x, boxTop - boxHeight, boxWidth, boxHeight, SOFT);
            stream.setStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
            stream.setLineWidth(0.7f * scale);
            stream.addRect(x, boxTop - boxHeight, boxWidth, boxHeight);
            stream.stroke();
            float centerX = left + (float) box.centerX() * scale;
            float centerY = top - (float) box.centerY() * scale;
            float labelSize = 9 * scale;
            centered(
                    box.label(),
                    bold,
                    labelSize,
                    centerX,
                    box.detail().isEmpty() ? centerY - 3 * scale : centerY + 2 * scale,
                    TEXT);
            if (!box.detail().isEmpty()) {
                centered(box.detail(), regular, 7.5f * scale, centerX, centerY - 10 * scale, MUTED);
            }
        }
        for (Arrow arrow : placed.arrows()) {
            if (!arrow.label().isEmpty()) {
                float size = 7 * scale;
                float labelWidth = stringWidth(arrow.label(), regular, size);
                float x = left + (float) arrow.labelX() * scale;
                float baseline = top - (float) arrow.labelY() * scale - 2.5f * scale;
                fill(x - labelWidth / 2 - 1.5f, baseline - 1.5f, labelWidth + 3, size + 2, WHITE);
                centered(arrow.label(), regular, size, x, baseline, MUTED);
            }
        }
        y -= height + 4;
        List<String> caption = wrap(figure.caption(), italic, 8.5f, width);
        for (String line : caption) {
            centered(line, italic, 8.5f, MARGIN + width / 2, y - 8.5f, MUTED);
            y -= 11;
        }
        y -= 4;
    }

    private void footers(String title) throws IOException {
        int pages = pdf.getNumberOfPages();
        String shown = title.length() > 70 ? title.substring(0, 67) + "..." : title;
        for (int number = 1; number <= pages; number++) {
            PDPage target = pdf.getPage(number - 1);
            try (PDPageContentStream footer = new PDPageContentStream(pdf, target, AppendMode.APPEND, true, true)) {
                stream = footer;
                text(shown, regular, 8, MARGIN, MARGIN - 8, MUTED);
                String numbering = "Page " + number + " of " + pages;
                text(numbering, regular, 8, MARGIN + width - stringWidth(numbering, regular, 8), MARGIN - 8, MUTED);
            }
        }
    }

    private void lines(List<String> lines, PDFont font, float size, float leading, float[] color, float indent)
            throws IOException {
        for (String line : lines) {
            ensure(leading);
            text(line, font, size, MARGIN + indent, y - size, color);
            y -= leading;
        }
    }

    private void text(String text, PDFont font, float size, float x, float baseline, float[] color) throws IOException {
        stream.beginText();
        stream.setNonStrokingColor(color[0], color[1], color[2]);
        stream.setFont(font, size);
        stream.newLineAtOffset(x, baseline);
        stream.showText(printable(text));
        stream.endText();
    }

    private void centered(String text, PDFont font, float size, float centerX, float baseline, float[] color)
            throws IOException {
        text(text, font, size, centerX - stringWidth(text, font, size) / 2, baseline, color);
    }

    private void fill(float x, float lowerY, float rectangleWidth, float height, float[] color) throws IOException {
        stream.setNonStrokingColor(color[0], color[1], color[2]);
        stream.addRect(x, lowerY, rectangleWidth, height);
        stream.fill();
    }

    private void rule() throws IOException {
        stream.setStrokingColor(LINE[0], LINE[1], LINE[2]);
        stream.setLineWidth(0.8f);
        stream.moveTo(MARGIN, y);
        stream.lineTo(MARGIN + width, y);
        stream.stroke();
        y -= 4;
    }

    private void ensure(float height) throws IOException {
        if (y - height < bottom) {
            newPage();
        }
    }

    private void newPage() throws IOException {
        stream.close();
        page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        stream = new PDPageContentStream(pdf, page);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    /** Breaks text into lines no wider than {@code available}; a word that is too long is cut where it must be. */
    static List<String> wrap(String text, PDFont font, float size, float available) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : printable(text).split(" ", -1)) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (stringWidth(candidate, font, size) <= available) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (stringWidth(rest, font, size) > available && rest.length() > 1) {
                int cut = fitting(rest, font, size, available);
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** How many characters of a long word fit, cutting after a dot, slash or similar when one is near the end. */
    private static int fitting(String word, PDFont font, float size, float available) throws IOException {
        int fits = 1;
        while (fits < word.length() && stringWidth(word.substring(0, fits + 1), font, size) <= available) {
            fits++;
        }
        for (int at = fits; at > fits / 2 && at > 1; at--) {
            if (".,/;:()-_".indexOf(word.charAt(at - 1)) >= 0) {
                return at;
            }
        }
        return fits;
    }

    private static float stringWidth(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(printable(text)) / 1000 * size;
    }

    /** The text in characters the built-in fonts have, on one line. */
    static String printable(String text) {
        StringBuilder printable = new StringBuilder(text.length());
        String source = text;
        for (int at = 0; at < source.length(); at++) {
            char character = source.charAt(at);
            switch (character) {
                case '\n', '\r', '\t', ' ' -> printable.append(' ');
                case '→', '⇒' -> printable.append("->");
                case '←' -> printable.append("<-");
                case '⇄', '↔' -> printable.append("<->");
                case '‘', '’' -> printable.append('\'');
                case '“', '”' -> printable.append('"');
                case '–', '—' -> printable.append('-');
                case '…' -> printable.append("...");
                case '•' -> printable.append('•');
                default ->
                    printable.append(
                            (character >= 32 && character <= 126) || (character >= 161 && character <= 255)
                                    ? character
                                    : '?');
            }
        }
        return printable.toString().replaceAll(" {2,}", " ").strip();
    }
}
