package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar.ReleaseLine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Keeps the release calendar as a small JSON file next to the advisories; a damaged file reads as missing. */
public final class ReleaseCalendarStore {

    static final String FILE_NAME = "release-calendar.json";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Path directory;

    public ReleaseCalendarStore(Path directory) {
        this.directory = directory;
    }

    public Path file() {
        return directory.resolve(FILE_NAME);
    }

    public Optional<ReleaseCalendar> load() {
        if (!Files.isRegularFile(file())) {
            return Optional.empty();
        }
        try {
            return Optional.of(decode(MAPPER.readTree(Files.readAllBytes(file()))));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    public void save(ReleaseCalendar calendar) {
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "release-calendar", ".tmp");
            try {
                Files.write(temporary, MAPPER.writeValueAsBytes(encode(calendar)));
                try {
                    Files.move(temporary, file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the release calendar in " + directory, e);
        }
    }

    private static ObjectNode encode(ReleaseCalendar calendar) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("fetchedAt", calendar.fetchedAt().toString());
        ObjectNode products = root.putObject("products");
        calendar.products().forEach((product, lines) -> {
            ArrayNode array = products.putArray(product);
            for (ReleaseLine line : lines) {
                ObjectNode node = array.addObject().put("line", line.line());
                line.released().ifPresent(date -> node.put("released", date.toString()));
                line.supportEnds().ifPresent(date -> node.put("supportEnds", date.toString()));
                line.latest().ifPresent(latest -> node.put("latest", latest));
            }
        });
        return root;
    }

    private static ReleaseCalendar decode(JsonNode root) {
        Map<String, List<ReleaseLine>> products = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> product : root.path("products").properties()) {
            List<ReleaseLine> lines = new ArrayList<>();
            for (JsonNode line : product.getValue()) {
                lines.add(new ReleaseLine(
                        line.path("line").asString(),
                        optional(line, "released").map(LocalDate::parse),
                        optional(line, "supportEnds").map(LocalDate::parse),
                        optional(line, "latest")));
            }
            products.put(product.getKey(), lines);
        }
        return new ReleaseCalendar(
                products, Instant.parse(root.path("fetchedAt").asString()));
    }

    private static Optional<String> optional(JsonNode node, String field) {
        return node.path(field).isString() ? Optional.of(node.path(field).asString()) : Optional.empty();
    }
}
