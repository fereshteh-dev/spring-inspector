package io.github.fereshtehdev.springinspector.project.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the line of a dependency in a POM's text, ignoring comments and the sections that only manage versions. */
final class PomLines {

    private static final List<String> SKIPPED = List.of("dependencyManagement", "build", "parent", "profiles");

    private final String xml;
    private final List<Range> skipped = new ArrayList<>();

    PomLines(String xml) {
        this.xml = xml;
        mark("<!--", "-->");
        for (String section : SKIPPED) {
            mark("<" + section + ">", "</" + section + ">");
        }
    }

    int lineOf(String artifactId) {
        Matcher matcher = Pattern.compile("<artifactId>\\s*" + Pattern.quote(artifactId) + "\\s*</artifactId>")
                .matcher(xml);
        while (matcher.find()) {
            if (!isSkipped(matcher.start())) {
                return line(matcher.start());
            }
        }
        int dependencies = xml.indexOf("<dependencies>");
        return dependencies < 0 ? 1 : line(dependencies);
    }

    private void mark(String open, String close) {
        for (int start = xml.indexOf(open); start >= 0; start = xml.indexOf(open, start + 1)) {
            int end = xml.indexOf(close, start);
            skipped.add(new Range(start, end < 0 ? xml.length() : end));
        }
    }

    private boolean isSkipped(int index) {
        return skipped.stream().anyMatch(range -> index >= range.start() && index < range.end());
    }

    private int line(int index) {
        return (int) xml.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }

    private record Range(int start, int end) {}
}
