package io.github.fereshtehdev.springinspector.intellij.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a finding's single-file unified diff to the text of a document, strictly: every context and removed line
 * must match, so a patch made for an older version of the file is refused instead of applied in the wrong place.
 * Documents use {@code \n}; a {@code \r} the patch carries from a Windows file is ignored.
 */
public final class PatchText {

    private static final Pattern HUNK = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    private PatchText() {}

    /** The patched text, or empty when the patch does not fit this text or names another file. */
    public static Optional<String> apply(String text, String patch, String file) {
        List<String> diff = patch.lines().map(PatchText::withoutCarriageReturn).toList();
        if (diff.size() < 3
                || !diff.get(0).equals("--- a/" + file)
                || !diff.get(1).equals("+++ b/" + file)) {
            return Optional.empty();
        }
        boolean endsWithBreak = text.endsWith("\n");
        List<String> lines = text.isEmpty() ? List.of() : List.of(text.split("\n", -1));
        if (endsWithBreak) {
            lines = lines.subList(0, lines.size() - 1);
        }
        List<String> result = new ArrayList<>();
        int copied = 0;
        int at = 2;
        while (at < diff.size()) {
            Matcher header = HUNK.matcher(diff.get(at));
            if (!header.lookingAt()) {
                return Optional.empty();
            }
            int oldStart = Integer.parseInt(header.group(1));
            int oldCount = header.group(2) == null ? 1 : Integer.parseInt(header.group(2));
            int line = oldCount == 0 ? oldStart : oldStart - 1;
            if (line < copied || line > lines.size()) {
                return Optional.empty();
            }
            result.addAll(lines.subList(copied, line));
            for (at++; at < diff.size() && !diff.get(at).startsWith("@@"); at++) {
                String entry = diff.get(at);
                if (entry.startsWith("\\")) {
                    continue;
                }
                char kind = entry.isEmpty() ? ' ' : entry.charAt(0);
                String content = entry.isEmpty() ? "" : entry.substring(1);
                if (kind == '+') {
                    result.add(content);
                } else if (kind == ' ' || kind == '-') {
                    if (line >= lines.size() || !lines.get(line).equals(content)) {
                        return Optional.empty();
                    }
                    if (kind == ' ') {
                        result.add(content);
                    }
                    line++;
                } else {
                    return Optional.empty();
                }
            }
            copied = line;
        }
        result.addAll(lines.subList(copied, lines.size()));
        String patched = String.join("\n", result);
        return Optional.of(endsWithBreak ? patched + "\n" : patched);
    }

    private static String withoutCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
