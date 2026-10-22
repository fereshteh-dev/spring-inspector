package io.github.fereshtehdev.springinspector.intellij.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

/**
 * A JSON value read with Jackson's streaming parser into maps, lists, strings, numbers and booleans, with typed and
 * forgiving accessors: a missing or mistyped field reads as its default instead of failing.
 */
final class JsonTree {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final int MAX_DEPTH = 64;

    private final @Nullable Object value;

    private JsonTree(@Nullable Object value) {
        this.value = value;
    }

    static JsonTree parse(String json) {
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), json)) {
            JsonToken first = parser.nextToken();
            if (first == null) {
                throw new IllegalArgumentException("The report is empty");
            }
            return new JsonTree(read(parser, first, 0));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("The report is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private static @Nullable Object read(JsonParser parser, JsonToken token, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("The report is nested too deeply");
        }
        return switch (token) {
            case START_OBJECT -> {
                Map<String, @Nullable Object> object = new LinkedHashMap<>();
                for (JsonToken next = parser.nextToken(); next == JsonToken.PROPERTY_NAME; next = parser.nextToken()) {
                    String name = parser.currentName();
                    object.put(name, read(parser, parser.nextToken(), depth + 1));
                }
                yield object;
            }
            case START_ARRAY -> {
                List<@Nullable Object> array = new ArrayList<>();
                for (JsonToken next = parser.nextToken(); next != null && next != JsonToken.END_ARRAY; next = parser.nextToken()) {
                    array.add(read(parser, next, depth + 1));
                }
                yield array;
            }
            case VALUE_STRING -> parser.getString();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getNumberValue();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            default -> null;
        };
    }

    JsonTree get(String name) {
        return value instanceof Map<?, ?> object ? new JsonTree(object.get(name)) : new JsonTree(null);
    }

    /** The field names of an object, in order; empty for anything else. */
    List<String> names() {
        if (!(value instanceof Map<?, ?> object)) {
            return List.of();
        }
        return object.keySet().stream().map(String::valueOf).toList();
    }

    List<JsonTree> items() {
        return value instanceof List<?> array ? array.stream().map(JsonTree::new).toList() : List.of();
    }

    String text() {
        return optionalText().orElse("");
    }

    Optional<String> optionalText() {
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }

    int number() {
        return value instanceof Number number ? number.intValue() : 0;
    }

    boolean bool() {
        return Boolean.TRUE.equals(value);
    }
}
