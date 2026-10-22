package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar.ReleaseLine;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads release facts from their public sources, as untrusted data: endoflife.date's product files (release lines with
 * release and end-of-support dates) and Maven Central's version lists (for products endoflife.date does not cover).
 */
final class ReleaseFacts {

    static final int MAX_BYTES = 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern VERSION = Pattern.compile("<version>\\s*([\\w.\\-]{1,64})\\s*</version>");
    private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.(\\d+)\\.\\d+");

    private ReleaseFacts() {}

    /** The release lines in an endoflife.date product file, such as {@code /api/spring-boot.json}. */
    static List<ReleaseLine> fromEndOfLife(byte[] json) {
        if (json.length > MAX_BYTES) {
            throw new IllegalArgumentException("The release data is larger than " + MAX_BYTES + " bytes");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("The release data is not valid JSON", e);
        }
        List<ReleaseLine> lines = new ArrayList<>();
        for (JsonNode cycle : root) {
            String line = cycle.path("cycle").asString("");
            if (!line.isBlank() && line.length() <= 32) {
                lines.add(new ReleaseLine(
                        line,
                        date(cycle.path("releaseDate")),
                        date(cycle.path("eol")),
                        cycle.path("latest").isString()
                                ? Optional.of(cycle.path("latest").asString())
                                : Optional.empty()));
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("The release data lists no release lines");
        }
        return lines;
    }

    /** The newest final release of each {@code major.minor} line in a Maven Central {@code maven-metadata.xml}. */
    static List<ReleaseLine> fromMavenMetadata(byte[] xml) {
        if (xml.length > MAX_BYTES) {
            throw new IllegalArgumentException("The version list is larger than " + MAX_BYTES + " bytes");
        }
        Map<String, MavenVersion> latest = new LinkedHashMap<>();
        Matcher version = VERSION.matcher(new String(xml, StandardCharsets.UTF_8));
        while (version.find()) {
            Matcher release = RELEASE.matcher(version.group(1));
            if (release.matches()) {
                String line = release.group(1) + "." + release.group(2);
                MavenVersion candidate = MavenVersion.parse(version.group(1));
                latest.merge(line, candidate, (a, b) -> a.compareTo(b) >= 0 ? a : b);
            }
        }
        if (latest.isEmpty()) {
            throw new IllegalArgumentException("The version list has no final releases");
        }
        List<ReleaseLine> lines = new ArrayList<>();
        latest.forEach((line, newest) ->
                lines.add(new ReleaseLine(line, Optional.empty(), Optional.empty(), Optional.of(newest.toString()))));
        return lines;
    }

    /** endoflife.date writes a date, or {@code true}/{@code false} when it has none. */
    private static Optional<LocalDate> date(JsonNode node) {
        if (!node.isString()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(node.asString()));
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }
}
