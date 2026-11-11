package io.github.fereshtehdev.springinspector.document.content;

import java.util.List;

/** One piece of a document. Text is plain: each writer escapes it for its own format. */
public sealed interface Block {

    /**
     * A section title.
     *
     * @param level 1 for a section, 2 for a part of one
     */
    record Heading(int level, String text) implements Block {}

    record Paragraph(String text) implements Block {}

    record Bullets(List<String> items) implements Block {

        public Bullets {
            items = List.copyOf(items);
        }
    }

    /**
     * Rows of cells under column titles.
     *
     * @param rows one list per row, with a cell for every column
     */
    record Table(List<Column> columns, List<List<String>> rows) implements Block {

        public Table {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
            for (List<String> row : rows) {
                if (row.size() != columns.size()) {
                    throw new IllegalArgumentException(
                            "A row has " + row.size() + " cells for " + columns.size() + " columns");
                }
            }
        }
    }

    /**
     * A table column.
     *
     * @param code whether the cells are code, such as class names and paths
     * @param weight its share of the table's width
     */
    record Column(String title, boolean code, int weight) {

        public static Column text(String title, int weight) {
            return new Column(title, false, weight);
        }

        public static Column code(String title, int weight) {
            return new Column(title, true, weight);
        }
    }

    record Figure(Diagram diagram, String caption) implements Block {}
}
