package io.github.fereshtehdev.springinspector.project.config;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.project.GatePolicy;
import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import io.github.fereshtehdev.springinspector.core.domain.project.PathPattern;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads {@code spring-inspector.yml}. The file comes from the inspected repository, so it is read as plain data with
 * tight limits: a tree of plain values (no custom types, aliases are not expanded), no duplicate keys, at most 64 KiB. Unknown settings are warnings, so a
 * newer file still works with an older tool; values of the wrong type are errors naming the setting.
 */
public final class ConfigFileReader {

    public static final String FILE_NAME = "spring-inspector.yml";

    static final int MAX_BYTES = 64 * 1024;

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(ms|s|m)?");
    private static final Set<String> TOP_LEVEL = Set.of(
            "gate",
            "categories",
            "rules",
            "ignore",
            "max-findings-per-category",
            "check-timeout",
            "changed-lines-only");

    private final YAMLMapper mapper = YAMLMapper.builder(YAMLFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .loadSettings(LoadSettings.builder()
                            .setMaxAliasesForCollections(10)
                            .setAllowRecursiveKeys(false)
                            .setAllowDuplicateKeys(false)
                            .setCodePointLimit(MAX_BYTES)
                            .build())
                    .build())
            .build();

    /** The project's {@value #FILE_NAME}, or the defaults when it has none. */
    public LoadedConfig read(Path projectRoot) {
        Path file = projectRoot.resolve(FILE_NAME);
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadedConfig(InspectorConfig.defaults(), List.of(), Optional.empty());
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(FILE_NAME + " must be a regular file, not a link or directory");
        }
        return read(file, FILE_NAME);
    }

    /** A configuration file the user named explicitly, for example to give CI its own settings. */
    public LoadedConfig readFile(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Configuration file not found: " + file);
        }
        return read(file, file.toString());
    }

    private LoadedConfig read(Path file, String name) {
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new IllegalArgumentException(name + " is larger than " + MAX_BYTES / 1024 + " KiB");
            }
            LoadedConfig parsed = parse(Files.readString(file, StandardCharsets.UTF_8), name);
            return new LoadedConfig(parsed.config(), parsed.warnings(), Optional.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + name, e);
        }
    }

    LoadedConfig parse(String yaml, String name) {
        JsonNode root;
        try {
            root = mapper.readTree(yaml);
        } catch (JacksonException e) {
            String line =
                    e.getLocation() == null ? "" : " (line " + e.getLocation().getLineNr() + ")";
            throw new IllegalArgumentException(name + " is not valid YAML" + line + ": " + e.getOriginalMessage(), e);
        }
        Settings settings = new Settings(name);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return new LoadedConfig(InspectorConfig.defaults(), List.of(), Optional.empty());
        }
        settings.requireObject(root, "the file");
        return new LoadedConfig(settings.read(root), settings.warnings, Optional.empty());
    }

    /** One pass over the tree, collecting warnings and failing fast on a wrong value. */
    private static final class Settings {

        private final String name;
        private final List<String> warnings = new ArrayList<>();

        Settings(String name) {
            this.name = name;
        }

        InspectorConfig read(JsonNode root) {
            warnUnknown(root, TOP_LEVEL, "");
            InspectorConfig defaults = InspectorConfig.defaults();
            JsonNode gate = object(root, "gate");
            JsonNode categories = object(root, "categories");
            JsonNode rules = object(root, "rules");
            warnUnknown(gate, Set.of("block-on", "minimum-score"), "gate.");
            warnUnknown(categories, Set.of("disabled"), "categories.");
            warnUnknown(rules, Set.of("disabled", "severity", "options"), "rules.");
            return new InspectorConfig(
                    disabledCategories(categories),
                    ruleIds(rules, "disabled", "rules.disabled"),
                    severities(rules),
                    duration(root, "check-timeout").orElse(defaults.checkTimeout()),
                    positiveInt(root, "max-findings-per-category").orElse(defaults.maxFindingsPerCategory()),
                    bool(root, "changed-lines-only").orElse(defaults.changedLinesOnly()),
                    gate(gate),
                    options(rules),
                    ignored(root));
        }

        private GatePolicy gate(JsonNode gate) {
            Severity blockOn = text(gate, "block-on", "gate.block-on")
                    .map(value -> severity(value, "gate.block-on"))
                    .orElse(GatePolicy.defaults().blockOn());
            OptionalInt minimumScore = OptionalInt.empty();
            JsonNode score = gate.get("minimum-score");
            if (score != null && !score.isNull()) {
                if (!score.isIntegralNumber() || score.asInt() < 0 || score.asInt() > 100) {
                    throw error("gate.minimum-score", "must be a whole number from 0 to 100");
                }
                minimumScore = OptionalInt.of(score.asInt());
            }
            return new GatePolicy(blockOn, minimumScore);
        }

        private Set<Category> disabledCategories(JsonNode categories) {
            Set<Category> disabled = EnumSet.noneOf(Category.class);
            for (String value : strings(categories, "disabled", "categories.disabled")) {
                String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
                Optional<Category> category = List.of(Category.values()).stream()
                        .filter(candidate -> candidate.name().equals(normalized))
                        .findFirst();
                disabled.add(
                        category.orElseThrow(() -> error("categories.disabled", "unknown category '" + value + "'")));
            }
            return disabled;
        }

        private Set<RuleId> ruleIds(JsonNode parent, String field, String path) {
            Set<RuleId> ids = new HashSet<>();
            for (String value : strings(parent, field, path)) {
                ids.add(ruleId(value, path));
            }
            return ids;
        }

        private Map<RuleId, Severity> severities(JsonNode rules) {
            Map<RuleId, Severity> severities = new HashMap<>();
            JsonNode node = object(rules, "severity");
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String path = "rules.severity." + entry.getKey();
                if (!entry.getValue().isString()) {
                    throw error(path, "must be one of blocker, major, minor, info");
                }
                severities.put(
                        ruleId(entry.getKey(), path), severity(entry.getValue().asString(), path));
            }
            return severities;
        }

        private Map<RuleId, RuleOptions> options(JsonNode rules) {
            Map<RuleId, RuleOptions> options = new HashMap<>();
            JsonNode node = object(rules, "options");
            for (Map.Entry<String, JsonNode> rule : node.properties()) {
                String path = "rules.options." + rule.getKey();
                requireObject(rule.getValue(), path);
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> option : rule.getValue().properties()) {
                    if (!option.getValue().isValueNode() || option.getValue().isNull()) {
                        throw error(path + "." + option.getKey(), "must be a single value");
                    }
                    values.put(option.getKey(), option.getValue().asString());
                }
                options.put(ruleId(rule.getKey(), path), new RuleOptions(values));
            }
            return options;
        }

        private List<PathPattern> ignored(JsonNode root) {
            return strings(root, "ignore", "ignore").stream()
                    .map(PathPattern::of)
                    .toList();
        }

        private Optional<Duration> duration(JsonNode parent, String field) {
            JsonNode node = parent.get(field);
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            Matcher matcher = DURATION.matcher(node.asString().strip());
            if (!node.isValueNode() || !matcher.matches() || Long.parseLong(matcher.group(1)) == 0) {
                throw error(field, "must be a positive duration such as 5s, 500ms or 2m");
            }
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2) == null ? "s" : matcher.group(2);
            return Optional.of(
                    switch (unit) {
                        case "ms" -> Duration.ofMillis(amount);
                        case "m" -> Duration.ofMinutes(amount);
                        default -> Duration.ofSeconds(amount);
                    });
        }

        private Optional<Integer> positiveInt(JsonNode parent, String field) {
            JsonNode node = parent.get(field);
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            if (!node.isIntegralNumber() || node.asInt() < 1) {
                throw error(field, "must be a whole number of at least 1");
            }
            return Optional.of(node.asInt());
        }

        private Optional<Boolean> bool(JsonNode parent, String field) {
            JsonNode node = parent.get(field);
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            if (!node.isBoolean()) {
                throw error(field, "must be true or false");
            }
            return Optional.of(node.asBoolean());
        }

        private Optional<String> text(JsonNode parent, String field, String path) {
            JsonNode node = parent.get(field);
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            if (!node.isString()) {
                throw error(path, "must be text");
            }
            return Optional.of(node.asString());
        }

        private List<String> strings(JsonNode parent, String field, String path) {
            JsonNode node = parent.get(field);
            if (node == null || node.isNull()) {
                return List.of();
            }
            if (!node.isArray()) {
                throw error(path, "must be a list");
            }
            List<String> values = new ArrayList<>();
            for (JsonNode element : node) {
                if (!element.isString()) {
                    throw error(path, "must be a list of text values");
                }
                values.add(element.asString());
            }
            return values;
        }

        private JsonNode object(JsonNode parent, String field) {
            JsonNode node = parent.get(field);
            if (isAbsent(node)) {
                return JsonNodeFactory.instance.objectNode();
            }
            requireObject(node, field);
            return node;
        }

        /** {@code key:} with nothing after it reads as an empty value; treat it like a missing key. */
        private static boolean isAbsent(@Nullable JsonNode node) {
            return node == null
                    || node.isNull()
                    || (node.isString() && node.asString().isEmpty());
        }

        void requireObject(JsonNode node, String path) {
            if (!node.isObject()) {
                throw error(path, "must be a mapping of settings");
            }
        }

        private Severity severity(String value, String path) {
            try {
                return Severity.valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw error(path, "must be one of blocker, major, minor, info, not '" + value + "'");
            }
        }

        private RuleId ruleId(String value, String path) {
            try {
                return RuleId.of(value.strip());
            } catch (IllegalArgumentException invalid) {
                throw error(path, "'" + value + "' is not a rule id");
            }
        }

        private void warnUnknown(JsonNode node, Set<String> known, String prefix) {
            for (String field : node.propertyNames()) {
                if (!known.contains(field)) {
                    warnings.add(name + ": unknown setting '" + prefix + field + "' is ignored");
                }
            }
        }

        private IllegalArgumentException error(String path, String problem) {
            return new IllegalArgumentException(name + ": " + path + " " + problem);
        }
    }
}
