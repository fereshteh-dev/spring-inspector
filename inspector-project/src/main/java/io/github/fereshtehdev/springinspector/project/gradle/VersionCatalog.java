package io.github.fereshtehdev.springinspector.project.gradle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionCatalog {

    private static final Pattern SECTION = Pattern.compile("\\[\\s*([\\w.-]+)\\s*]");
    private static final Pattern ENTRY = Pattern.compile("([\\w.-]+)\\s*=\\s*(.+)");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern FIELD = Pattern.compile("([\\w.]+)\\s*=\\s*\"([^\"]*)\"");

    record Library(String group, String name, Optional<String> version) {}

    record Plugin(String id, Optional<String> version) {}

    private final Map<String, String> versions = new HashMap<>();
    private final List<Library> libraries = new ArrayList<>();
    private final Map<String, Library> byAccessor = new HashMap<>();
    private final List<Plugin> plugins = new ArrayList<>();

    private VersionCatalog() {}

    static VersionCatalog empty() {
        return new VersionCatalog();
    }

    static VersionCatalog parse(String toml) {
        VersionCatalog catalog = new VersionCatalog();
        String section = "";
        for (String rawLine : toml.lines().toList()) {
            String line = withoutComment(rawLine).strip();
            Matcher sectionMatcher = SECTION.matcher(line);
            Matcher entry = ENTRY.matcher(line);
            if (sectionMatcher.matches()) {
                section = sectionMatcher.group(1);
            } else if (entry.matches()) {
                catalog.add(section, entry.group(1), entry.group(2).strip());
            }
        }
        return catalog;
    }

    List<Library> libraries() {
        return List.copyOf(libraries);
    }

    /** The library that {@code libs.<accessor>} refers to in a build script. */
    Optional<Library> library(String accessor) {
        return Optional.ofNullable(byAccessor.get(accessor(accessor)));
    }

    /** Gradle turns {@code -}, {@code _} and {@code .} in aliases into the same accessor separator. */
    private static String accessor(String alias) {
        return alias.replaceAll("[-_.]", ".");
    }

    List<Plugin> plugins() {
        return List.copyOf(plugins);
    }

    private void add(String section, String key, String value) {
        Map<String, String> fields = fields(value);
        switch (section) {
            case "versions" -> quoted(value).ifPresent(version -> versions.put(key, version));
            case "libraries" ->
                library(value, fields).ifPresent(library -> {
                    libraries.add(library);
                    byAccessor.put(accessor(key), library);
                });
            case "plugins" -> plugin(value, fields).ifPresent(plugins::add);
            default -> {}
        }
    }

    private Optional<Library> library(String value, Map<String, String> fields) {
        if (!value.startsWith("{")) {
            return quoted(value)
                    .map(notation -> notation.split(":", 3))
                    .filter(parts -> parts.length >= 2)
                    .map(parts -> new Library(
                            parts[0], parts[1], parts.length == 3 ? Optional.of(parts[2]) : Optional.empty()));
        }
        Optional<String[]> module = Optional.ofNullable(fields.get("module"))
                .map(m -> m.split(":", 2))
                .filter(parts -> parts.length == 2);
        String group = module.map(parts -> parts[0]).orElse(fields.getOrDefault("group", ""));
        String name = module.map(parts -> parts[1]).orElse(fields.getOrDefault("name", ""));
        return group.isEmpty() ? Optional.empty() : Optional.of(new Library(group, name, version(fields)));
    }

    private Optional<Plugin> plugin(String value, Map<String, String> fields) {
        if (!value.startsWith("{")) {
            return quoted(value)
                    .map(notation -> notation.split(":", 2))
                    .map(parts -> new Plugin(parts[0], parts.length == 2 ? Optional.of(parts[1]) : Optional.empty()));
        }
        return Optional.ofNullable(fields.get("id")).map(id -> new Plugin(id, version(fields)));
    }

    private Optional<String> version(Map<String, String> fields) {
        String reference = fields.get("version.ref");
        if (reference != null) {
            return Optional.ofNullable(versions.get(reference));
        }
        return Optional.ofNullable(fields.get("version"));
    }

    private static Map<String, String> fields(String inlineTable) {
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD.matcher(inlineTable);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        return fields;
    }

    private static Optional<String> quoted(String value) {
        Matcher matcher = QUOTED.matcher(value);
        return matcher.lookingAt() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String withoutComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
