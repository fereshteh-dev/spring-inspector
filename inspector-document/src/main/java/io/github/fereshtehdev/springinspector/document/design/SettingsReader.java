package io.github.fereshtehdev.springinspector.document.design;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.RuntimeSettings;
import io.github.fereshtehdev.springinspector.javamodel.settings.Setting;
import io.github.fereshtehdev.springinspector.javamodel.settings.SettingsParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the facts worth documenting out of Spring Boot's settings files. Addresses, user names and anything that
 * could be a credential are never copied: a database is reported by its product, a model by its name.
 */
final class SettingsReader {

    private static final Pattern DATABASE_URL = Pattern.compile("^(?:jdbc|r2dbc):(?:pool:)?([a-z0-9]+):");
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{[^:}]+(?::([^}]*))?}$");
    private static final Pattern SAFE_VALUE = Pattern.compile("[\\w.:/@ -]{1,80}");
    private static final Map<String, String> DATABASES = Map.of(
            "postgresql", "PostgreSQL",
            "mysql", "MySQL",
            "mariadb", "MariaDB",
            "oracle", "Oracle Database",
            "sqlserver", "Microsoft SQL Server",
            "h2", "H2",
            "hsqldb", "HSQLDB",
            "db2", "IBM Db2",
            "sqlite", "SQLite");
    private static final Map<String, String> STORES_BY_PREFIX = Map.of(
            "spring.data.mongodb.", "MongoDB",
            "spring.mongodb.", "MongoDB",
            "spring.data.redis.", "Redis",
            "spring.elasticsearch.", "Elasticsearch",
            "spring.cassandra.", "Cassandra",
            "spring.neo4j.", "Neo4j");
    private static final Map<String, String> PROVIDERS = Map.of(
            "openai", "OpenAI",
            "anthropic", "Anthropic",
            "ollama", "Ollama",
            "azure", "Azure-OpenAI",
            "bedrock", "Amazon-Bedrock",
            "google", "Google",
            "vertex", "Vertex-AI",
            "mistralai", "Mistral-AI",
            "deepseek", "DeepSeek");
    private static final String VECTOR_STORE = "spring.ai.vectorstore.";

    private SettingsReader() {}

    static RuntimeSettings of(List<SourceFile> settingsFiles) {
        List<Setting> settings = new ArrayList<>();
        for (SourceFile file : settingsFiles) {
            try {
                settings.addAll(SettingsParser.parse(file));
            } catch (RuntimeException unreadable) {
                // A settings file that cannot be parsed tells the document nothing; the rules report it.
            }
        }
        Set<String> stores = new TreeSet<>();
        Set<String> profiles = new TreeSet<>();
        Set<String> models = new TreeSet<>();
        Set<String> vectorStores = new TreeSet<>();
        for (Setting setting : settings) {
            String key = setting.key().toLowerCase(Locale.ROOT);
            setting.profile().ifPresent(profiles::add);
            if (key.equals("spring.datasource.url") || key.equals("spring.r2dbc.url")) {
                stores.add(database(setting.value()));
            }
            STORES_BY_PREFIX.forEach((prefix, store) -> {
                if (key.startsWith(prefix)) {
                    stores.add(store);
                }
            });
            if (key.startsWith(VECTOR_STORE)) {
                String kind = key.substring(VECTOR_STORE.length()).split("\\.", -1)[0];
                vectorStores.add(kind);
            }
            model(key, setting.value()).ifPresent(models::add);
        }
        return new RuntimeSettings(
                first(settings, "spring.application.name"),
                first(settings, "server.port"),
                List.copyOf(stores),
                List.copyOf(profiles),
                List.copyOf(models),
                List.copyOf(vectorStores));
    }

    private static Optional<String> first(List<Setting> settings, String key) {
        return settings.stream()
                .filter(setting -> setting.is(key))
                .sorted((left, right) -> Boolean.compare(
                        left.profile().isPresent(), right.profile().isPresent()))
                .map(setting -> plain(setting.value()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Only the product is read from the address; the address itself is never kept. */
    private static String database(String url) {
        String text = url.strip();
        Matcher placeholder = PLACEHOLDER.matcher(text);
        if (placeholder.matches()) {
            text = placeholder.group(1) == null ? "" : placeholder.group(1).strip();
        }
        Matcher matcher = DATABASE_URL.matcher(text);
        if (!matcher.find()) {
            return "SQL database";
        }
        String product = matcher.group(1);
        return DATABASES.getOrDefault(product, product);
    }

    /** {@code OpenAI chat model: gpt-5} for {@code spring.ai.openai.chat.options.model=gpt-5}. */
    private static Optional<String> model(String key, String value) {
        String[] parts = key.split("\\.", -1);
        if (parts.length < 5 || !key.startsWith("spring.ai.") || !parts[parts.length - 1].equals("model")) {
            return Optional.empty();
        }
        String name = plain(value).orElse("set by the environment");
        return Optional.of(PROVIDERS.getOrDefault(parts[2], parts[2]) + " " + parts[3] + " model: " + name);
    }

    /**
     * A value that is safe and useful to print: a placeholder gives its default, and anything that does not look
     * like a plain name is left out.
     */
    private static Optional<String> plain(String value) {
        String text = value.strip();
        Matcher placeholder = PLACEHOLDER.matcher(text);
        if (placeholder.matches()) {
            text = placeholder.group(1) == null ? "" : placeholder.group(1).strip();
        }
        return SAFE_VALUE.matcher(text).matches() && !text.contains("${") ? Optional.of(text) : Optional.empty();
    }
}
