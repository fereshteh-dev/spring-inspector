package io.github.fereshtehdev.springinspector.testkit;

import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.javamodel.patch.LineIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies a single-file unified diff strictly, as {@code git apply} would without fuzz. */
public final class UnifiedDiffs {

    private static final Pattern HUNK = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");
    private static final String NO_NEWLINE = "\\ No newline at end of file";

    private UnifiedDiffs() {}

    public static String apply(String original, Patch patch) {
        List<String> oldLines = LineIndex.of(original).lines();
        List<String> diff = diffLines(patch.unifiedDiff());
        if (diff.size() < 2 || !diff.get(0).startsWith("--- ") || !diff.get(1).startsWith("+++ ")) {
            throw new IllegalArgumentException("Patch has no file header:\n" + patch.unifiedDiff());
        }
        StringBuilder result = new StringBuilder();
        int copied = 0;
        int at = 2;
        while (at < diff.size()) {
            Matcher header = HUNK.matcher(diff.get(at));
            if (!header.lookingAt()) {
                throw new IllegalArgumentException("Expected a hunk header: " + diff.get(at));
            }
            int oldStart = Integer.parseInt(header.group(1));
            int oldCount = header.group(2) == null ? 1 : Integer.parseInt(header.group(2));
            int firstOld = oldCount == 0 ? oldStart : oldStart - 1;
            if (firstOld < copied) {
                throw new IllegalArgumentException("Hunks overlap or are out of order");
            }
            oldLines.subList(copied, firstOld).forEach(result::append);
            int line = firstOld;
            for (at++; at < diff.size() && !diff.get(at).startsWith("@@"); at++) {
                String text = withLineBreak(diff, at);
                switch (diff.get(at).charAt(0)) {
                    case ' ', '-' -> {
                        if (line >= oldLines.size() || !oldLines.get(line).equals(text)) {
                            throw new IllegalArgumentException(
                                    "Patch does not match line " + (line + 1) + ": " + diff.get(at));
                        }
                        if (diff.get(at).charAt(0) == ' ') {
                            result.append(text);
                        }
                        line++;
                    }
                    case '+' -> result.append(text);
                    default -> throw new IllegalArgumentException("Unexpected patch line: " + diff.get(at));
                }
                if (at + 1 < diff.size() && diff.get(at + 1).equals(NO_NEWLINE)) {
                    at++;
                }
            }
            copied = line;
        }
        oldLines.subList(copied, oldLines.size()).forEach(result::append);
        return result.toString();
    }

    /** Patch lines without their final {@code \n}; a {@code \r} before it belongs to the content. */
    private static List<String> diffLines(String diff) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int end = diff.indexOf('\n'); end >= 0; end = diff.indexOf('\n', start)) {
            lines.add(diff.substring(start, end));
            start = end + 1;
        }
        if (start < diff.length()) {
            lines.add(diff.substring(start));
        }
        return lines;
    }

    private static String withLineBreak(List<String> diff, int at) {
        String content = diff.get(at).substring(1);
        boolean noNewline = at + 1 < diff.size() && diff.get(at + 1).equals(NO_NEWLINE);
        return noNewline ? content : content + "\n";
    }
}
