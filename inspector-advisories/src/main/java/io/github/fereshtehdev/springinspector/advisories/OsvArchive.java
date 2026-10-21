package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.advisories.Advisory.AffectedPackage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads OSV's {@code Maven/all.zip}: one JSON document per advisory in the OSV schema. Downloaded data is bounded
 * before it is read, and withdrawn advisories and non-Maven packages are dropped.
 */
public final class OsvArchive {

    static final int MAX_ENTRIES = 200_000;
    static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private OsvArchive() {}

    public static List<Advisory> read(byte[] zip) {
        List<Advisory> advisories = new ArrayList<>();
        long total = 0;
        int entries = 0;
        int documents = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("The archive has more than " + MAX_ENTRIES + " entries");
                }
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    continue;
                }
                documents++;
                byte[] json = readBounded(in);
                total += json.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException(
                            "The archive expands to more than " + MAX_TOTAL_BYTES + " bytes");
                }
                parse(json).ifPresent(advisories::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Not a readable OSV archive", e);
        }
        if (documents == 0) {
            throw new IllegalArgumentException("Not an OSV archive: it holds no advisory documents");
        }
        return advisories;
    }

    static Optional<Advisory> parse(byte[] json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (root.has("withdrawn") || !root.path("id").isString()) {
            return Optional.empty();
        }
        List<AffectedPackage> packages = new ArrayList<>();
        for (JsonNode affected : root.path("affected")) {
            JsonNode pkg = affected.path("package");
            if (pkg.path("ecosystem").asString("").equals("Maven")
                    && pkg.path("name").isString()) {
                packages.add(affectedPackage(pkg.path("name").asString(), affected));
            }
        }
        if (packages.isEmpty()) {
            return Optional.empty();
        }
        List<String> aliases = new ArrayList<>();
        root.path("aliases").forEach(alias -> aliases.add(alias.asString("")));
        String id = root.path("id").asString();
        String summary = root.path("summary").asString("").isBlank()
                ? firstLine(root.path("details").asString(id))
                : root.path("summary").asString();
        AdvisorySeverity severity = AdvisorySeverity.of(
                root.path("database_specific").path("severity").asString(""));
        return Optional.of(new Advisory(id, aliases, summary, severity, packages));
    }

    private static AffectedPackage affectedPackage(String coordinate, JsonNode affected) {
        List<AffectedRange> ranges = new ArrayList<>();
        for (JsonNode range : affected.path("ranges")) {
            ranges.addAll(ranges(range.path("events")));
        }
        Set<String> versions = new HashSet<>();
        if (ranges.isEmpty()) {
            affected.path("versions").forEach(version -> versions.add(version.asString("")));
        }
        return new AffectedPackage(coordinate, ranges, versions);
    }

    /** OSV lists events in order: each introduced opens a range that the next fixed or last_affected closes. */
    private static List<AffectedRange> ranges(JsonNode events) {
        List<AffectedRange> ranges = new ArrayList<>();
        Optional<String> introduced = Optional.empty();
        boolean open = false;
        for (JsonNode event : events) {
            if (event.has("introduced")) {
                String version = event.path("introduced").asString("0");
                introduced = version.equals("0") ? Optional.empty() : Optional.of(version);
                open = true;
            } else if (event.has("fixed") && open) {
                ranges.add(new AffectedRange(
                        introduced, Optional.of(event.path("fixed").asString()), Optional.empty()));
                open = false;
            } else if (event.has("last_affected") && open) {
                ranges.add(new AffectedRange(
                        introduced,
                        Optional.empty(),
                        Optional.of(event.path("last_affected").asString())));
                open = false;
            }
        }
        if (open) {
            ranges.add(new AffectedRange(introduced, Optional.empty(), Optional.empty()));
        }
        return ranges;
    }

    private static String firstLine(String text) {
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }

    private static byte[] readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long read = 0;
        for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
            read += n;
            if (read > MAX_ENTRY_BYTES) {
                throw new IllegalArgumentException("An archive entry is larger than " + MAX_ENTRY_BYTES + " bytes");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
