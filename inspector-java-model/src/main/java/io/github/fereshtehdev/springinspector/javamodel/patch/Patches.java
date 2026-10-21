package io.github.fereshtehdev.springinspector.javamodel.patch;

import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds unified diffs straight from text edits, so no general-purpose diff algorithm is needed. */
public final class Patches {

    private static final int CONTEXT_LINES = 3;

    private Patches() {}

    public static String apply(String text, List<TextEdit> edits) {
        StringBuilder edited = new StringBuilder(text.length());
        int copied = 0;
        for (TextEdit edit : ordered(edits, text.length())) {
            edited.append(text, copied, edit.start()).append(edit.replacement());
            copied = edit.end();
        }
        return edited.append(text, copied, text.length()).toString();
    }

    public static Patch unifiedDiff(SourceFile file, List<TextEdit> edits) {
        String text = file.content();
        LineIndex lines = LineIndex.of(text);
        List<Change> changes = changes(text, lines, ordered(edits, text.length()));
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("A patch must change something in " + file.path());
        }
        String path = file.path().toString().replace('\\', '/');
        StringBuilder diff = new StringBuilder("--- a/" + path + "\n+++ b/" + path + "\n");
        int shift = 0;
        for (List<Change> hunk : hunks(changes)) {
            shift = appendHunk(diff, hunk, lines, shift);
        }
        return new Patch(diff.toString());
    }

    private static List<TextEdit> ordered(List<TextEdit> edits, int length) {
        List<TextEdit> ordered = edits.stream()
                .sorted(Comparator.comparingInt(TextEdit::start).thenComparingInt(TextEdit::end))
                .toList();
        int previousEnd = 0;
        for (TextEdit edit : ordered) {
            if (edit.end() > length) {
                throw new IllegalArgumentException("Edit " + edit + " ends after the text (" + length + ")");
            }
            if (edit.start() < previousEnd) {
                throw new IllegalArgumentException("Edits overlap at offset " + edit.start());
            }
            previousEnd = edit.end();
        }
        return ordered;
    }

    private static List<Change> changes(String text, LineIndex lines, List<TextEdit> edits) {
        List<List<TextEdit>> groups = new ArrayList<>();
        int groupFirst = -1;
        int groupEnd = -1;
        for (TextEdit edit : edits) {
            int first = lines.lineOf(edit.start());
            int end = lineEnd(lines, edit);
            boolean sharesLines = first < groupEnd || (first == groupFirst && first == groupEnd);
            if (groups.isEmpty() || !sharesLines) {
                groups.add(new ArrayList<>());
                groupFirst = first;
                groupEnd = end;
            }
            groups.getLast().add(edit);
            groupEnd = Math.max(groupEnd, end);
        }
        List<Change> changes = new ArrayList<>();
        for (List<TextEdit> group : groups) {
            Change change = change(text, lines, group).withoutUnchangedEnds(lines);
            if (!change.isEmpty()) {
                changes.add(change);
            }
        }
        return changes;
    }

    private static int lineEnd(LineIndex lines, TextEdit edit) {
        int first = lines.lineOf(edit.start());
        int last = edit.end() > edit.start() ? lines.lineOf(edit.end() - 1) : first;
        return Math.min(last + 1, lines.lineCount());
    }

    private static Change change(String text, LineIndex lines, List<TextEdit> group) {
        int first = lines.lineOf(group.getFirst().start());
        int end = group.stream().mapToInt(edit -> lineEnd(lines, edit)).max().orElse(first);
        int blockStart = lines.start(first);
        String block = text.substring(blockStart, lines.start(end));
        List<TextEdit> relative = group.stream()
                .map(edit -> new TextEdit(edit.start() - blockStart, edit.end() - blockStart, edit.replacement()))
                .toList();
        return new Change(first, end, LineIndex.of(apply(block, relative)).lines());
    }

    private static List<List<Change>> hunks(List<Change> changes) {
        List<List<Change>> hunks = new ArrayList<>();
        Change previous = null;
        for (Change change : changes) {
            if (previous == null || change.first() - previous.end() > 2 * CONTEXT_LINES) {
                hunks.add(new ArrayList<>());
            }
            hunks.getLast().add(change);
            previous = change;
        }
        return hunks;
    }

    private static int appendHunk(StringBuilder diff, List<Change> hunk, LineIndex lines, int shift) {
        int oldStart = Math.max(0, hunk.getFirst().first() - CONTEXT_LINES);
        int oldEnd = Math.min(lines.lineCount(), hunk.getLast().end() + CONTEXT_LINES);
        StringBuilder body = new StringBuilder();
        int line = oldStart;
        int newCount = 0;
        for (Change change : hunk) {
            for (; line < change.first(); line++, newCount++) {
                appendLine(body, ' ', lines.line(line));
            }
            for (String removed : change.oldLines(lines)) {
                appendLine(body, '-', removed);
            }
            for (String added : change.newLines()) {
                appendLine(body, '+', added);
                newCount++;
            }
            line = change.end();
        }
        for (; line < oldEnd; line++, newCount++) {
            appendLine(body, ' ', lines.line(line));
        }
        int oldCount = oldEnd - oldStart;
        diff.append("@@ -")
                .append(range(oldStart + 1, oldCount))
                .append(" +")
                .append(range(oldStart + 1 + shift, newCount))
                .append(" @@\n")
                .append(body);
        return shift + newCount - oldCount;
    }

    private static String range(int start, int count) {
        if (count == 1) {
            return String.valueOf(start);
        }
        return (count == 0 ? start - 1 : start) + "," + count;
    }

    private static void appendLine(StringBuilder diff, char marker, String line) {
        diff.append(marker).append(line);
        if (!line.endsWith("\n") && !line.endsWith("\r")) {
            diff.append("\n\\ No newline at end of file\n");
        }
    }

    /** Old lines {@code [first, end)} become {@code newLines}. */
    private record Change(int first, int end, List<String> newLines) {

        Change {
            newLines = List.copyOf(newLines);
        }

        List<String> oldLines(LineIndex lines) {
            List<String> old = new ArrayList<>(end - first);
            for (int line = first; line < end; line++) {
                old.add(lines.line(line));
            }
            return old;
        }

        boolean isEmpty() {
            return first == end && newLines.isEmpty();
        }

        /** Edits work on whole lines; lines they leave as they were belong in the context, not the change. */
        Change withoutUnchangedEnds(LineIndex lines) {
            List<String> old = oldLines(lines);
            int prefix = 0;
            while (prefix < old.size()
                    && prefix < newLines.size()
                    && old.get(prefix).equals(newLines.get(prefix))) {
                prefix++;
            }
            int suffix = 0;
            while (suffix < old.size() - prefix
                    && suffix < newLines.size() - prefix
                    && old.get(old.size() - 1 - suffix).equals(newLines.get(newLines.size() - 1 - suffix))) {
                suffix++;
            }
            return new Change(first + prefix, end - suffix, newLines.subList(prefix, newLines.size() - suffix));
        }
    }
}
