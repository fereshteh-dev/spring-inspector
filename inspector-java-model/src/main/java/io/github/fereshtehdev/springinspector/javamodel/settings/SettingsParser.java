package io.github.fereshtehdev.springinspector.javamodel.settings;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.javamodel.patch.LineIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

/**
 * Flattens Spring Boot properties and YAML files into settings with their lines. Files come from the inspected
 * repository: YAML is only composed into nodes, never constructed into objects, and flattening is bounded so shared
 * aliases cannot multiply into millions of settings.
 */
public final class SettingsParser {

    static final int MAX_SETTINGS = 20_000;
    private static final int MAX_DEPTH = 40;
    private static final String PROFILE_KEY = "spring.config.activate.on-profile";
    private static final String LEGACY_PROFILE_KEY = "spring.profiles";

    private SettingsParser() {}

    /** Throws {@link IllegalArgumentException} when the file is not valid properties or YAML. */
    public static List<Setting> parse(SourceFile file) {
        Optional<String> fileProfile = SettingsFile.profileOf(file.path());
        String name = file.path().toString();
        List<List<Setting>> documents = name.endsWith(".properties")
                ? properties(file.content(), fileProfile)
                : yaml(file.content(), fileProfile);
        List<Setting> settings = new ArrayList<>();
        for (List<Setting> document : documents) {
            settings.addAll(withDocumentProfile(document));
        }
        return settings;
    }

    /** A document that activates on a profile applies all of its settings to that profile only. */
    private static List<Setting> withDocumentProfile(List<Setting> document) {
        Optional<String> profile = document.stream()
                .filter(setting -> setting.is(PROFILE_KEY) || setting.is(LEGACY_PROFILE_KEY))
                .map(Setting::value)
                .findFirst();
        if (profile.isEmpty()) {
            return document;
        }
        return document.stream()
                .map(setting -> new Setting(
                        setting.key(),
                        setting.value(),
                        setting.line(),
                        profile,
                        setting.valueStart(),
                        setting.valueEnd()))
                .toList();
    }

    private static List<List<Setting>> properties(String content, Optional<String> profile) {
        List<List<Setting>> documents = new ArrayList<>();
        documents.add(new ArrayList<>());
        LineIndex lines = LineIndex.of(content);
        int line = 0;
        while (line < lines.lineCount()) {
            int first = line;
            String text = stripLineBreak(lines.line(line));
            StringBuilder logical = new StringBuilder(text);
            while (endsWithContinuation(logical) && line + 1 < lines.lineCount()) {
                logical.setLength(logical.length() - 1);
                line++;
                logical.append(stripLineBreak(lines.line(line)).stripLeading());
            }
            line++;
            String trimmed = logical.toString().strip();
            if (trimmed.equals("#---") || trimmed.equals("!---")) {
                documents.add(new ArrayList<>());
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                documents
                        .getLast()
                        .add(property(logical.toString(), lines.start(first), first + 1, profile, first == line - 1));
            }
            if (documents.stream().mapToInt(List::size).sum() > MAX_SETTINGS) {
                throw new IllegalArgumentException("More than " + MAX_SETTINGS + " settings");
            }
        }
        return documents;
    }

    private static Setting property(String text, int lineStart, int line, Optional<String> profile, boolean oneLine) {
        int keyStart = 0;
        while (keyStart < text.length() && Character.isWhitespace(text.charAt(keyStart))) {
            keyStart++;
        }
        int separator = keyStart;
        while (separator < text.length()) {
            char c = text.charAt(separator);
            if (c == '\\') {
                separator += 2;
                continue;
            }
            if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                break;
            }
            separator++;
        }
        separator = Math.min(separator, text.length());
        String key = unescape(text.substring(keyStart, separator));
        int valueStart = separator;
        while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart < text.length() && (text.charAt(valueStart) == '=' || text.charAt(valueStart) == ':')) {
            valueStart++;
            while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
                valueStart++;
            }
        }
        String raw = text.substring(valueStart).stripTrailing();
        boolean replaceable = oneLine && raw.indexOf('\\') < 0;
        return new Setting(
                key,
                unescape(raw),
                line,
                profile,
                replaceable ? OptionalInt.of(lineStart + valueStart) : OptionalInt.empty(),
                replaceable ? OptionalInt.of(lineStart + valueStart + raw.length()) : OptionalInt.empty());
    }

    private static List<List<Setting>> yaml(String content, Optional<String> profile) {
        LoadSettings settings = LoadSettings.builder()
                .setMaxAliasesForCollections(50)
                .setAllowRecursiveKeys(false)
                .setCodePointLimit(4 * 1024 * 1024)
                .build();
        List<List<Setting>> documents = new ArrayList<>();
        try {
            for (Node document : new Compose(settings).composeAllFromString(content)) {
                List<Setting> flattened = new ArrayList<>();
                flatten(document, "", 0, profile, content, flattened);
                documents.add(flattened);
            }
        } catch (YamlEngineException e) {
            throw new IllegalArgumentException("Not valid YAML: " + e.getMessage(), e);
        }
        return documents;
    }

    private static void flatten(
            Node node, String key, int depth, Optional<String> profile, String content, List<Setting> out) {
        if (depth > MAX_DEPTH || out.size() > MAX_SETTINGS) {
            throw new IllegalArgumentException("Settings nest too deeply or are too many");
        }
        switch (node) {
            case MappingNode mapping -> {
                for (NodeTuple entry : mapping.getValue()) {
                    if (entry.getKeyNode() instanceof ScalarNode name) {
                        String child = key.isEmpty() ? name.getValue() : key + "." + name.getValue();
                        flatten(entry.getValueNode(), child, depth + 1, profile, content, out);
                    }
                }
            }
            case SequenceNode sequence -> {
                List<Node> items = sequence.getValue();
                for (int i = 0; i < items.size(); i++) {
                    flatten(items.get(i), key + "[" + i + "]", depth + 1, profile, content, out);
                }
            }
            case ScalarNode scalar -> out.add(scalar(scalar, key, profile, content));
            default -> {
                // Anchors resolve to the nodes above; nothing else carries settings.
            }
        }
    }

    private static Setting scalar(ScalarNode scalar, String key, Optional<String> profile, String content) {
        int line = scalar.getStartMark().map(mark -> mark.getLine() + 1).orElse(1);
        OptionalInt start = OptionalInt.empty();
        OptionalInt end = OptionalInt.empty();
        Optional<Mark> startMark = scalar.getStartMark();
        Optional<Mark> endMark = scalar.getEndMark();
        if (scalar.getScalarStyle() == ScalarStyle.PLAIN && startMark.isPresent() && endMark.isPresent()) {
            int from = startMark.get().getIndex();
            int to = endMark.get().getIndex();
            if (to <= content.length() && content.substring(from, to).equals(scalar.getValue())) {
                start = OptionalInt.of(from);
                end = OptionalInt.of(to);
            }
        }
        return new Setting(key, scalar.getValue(), line, profile, start, end);
    }

    private static boolean endsWithContinuation(CharSequence text) {
        int backslashes = 0;
        for (int i = text.length() - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static String stripLineBreak(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) {
            end--;
        }
        return line.substring(0, end);
    }

    private static String unescape(String text) {
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 == text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < text.length()) {
                        try {
                            out.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException notUnicode) {
                            out.append('u');
                        }
                    } else {
                        out.append('u');
                    }
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }
}
