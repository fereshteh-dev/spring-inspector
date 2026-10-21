package io.github.fereshtehdev.springinspector.javamodel.settings;

import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.domain.project.RuleOptions;
import io.github.fereshtehdev.springinspector.javamodel.patch.Patches;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A Spring Boot settings file ({@code application*.properties} or {@code .yml}) as a check sees it. */
public final class SettingsFile {

    private static final Pattern NAME =
            Pattern.compile("(?:application|bootstrap)(?:-([A-Za-z0-9_.-]+))?\\.(?:properties|ya?ml)");
    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "development", "local", "test", "it");

    private final SourceFile source;
    private final List<Setting> settings;
    private final RuleOptions options;

    public SettingsFile(SourceFile source, List<Setting> settings, RuleOptions options) {
        this.source = source;
        this.settings = List.copyOf(settings);
        this.options = options;
    }

    /** {@code application.yml}, {@code application-prod.properties}, {@code bootstrap.yaml} and the like. */
    public static boolean isSettingsFile(Path path) {
        Path name = path.getFileName();
        return name != null && NAME.matcher(name.toString()).matches();
    }

    /** The profile a file name names, such as {@code prod} for {@code application-prod.yml}. */
    static Optional<String> profileOf(Path path) {
        Path name = path.getFileName();
        if (name == null) {
            return Optional.empty();
        }
        Matcher matcher = NAME.matcher(name.toString());
        return matcher.matches() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }

    /** Whether a setting only applies while developing or testing, where conveniences such as H2's console belong. */
    public static boolean isDevelopmentOnly(Setting setting) {
        return setting.profile()
                .map(profile -> List.of(profile.split("[,&|!() ]+", -1)).stream()
                        .anyMatch(part -> DEVELOPMENT_PROFILES.contains(part.strip())))
                .orElse(false);
    }

    public Path path() {
        return source.path();
    }

    public SourceFile source() {
        return source;
    }

    public List<Setting> settings() {
        return settings;
    }

    public List<Setting> find(String key) {
        return settings.stream().filter(setting -> setting.is(key)).toList();
    }

    public RuleOptions options() {
        return options;
    }

    /** The same parsed file seen by another check, with that check's options. */
    public SettingsFile withOptions(RuleOptions checkOptions) {
        return new SettingsFile(source, settings, checkOptions);
    }

    public Location location(Setting setting) {
        return new Location(path(), LineRange.line(setting.line()));
    }

    public String snippet(Setting setting) {
        return source.snippet(LineRange.line(setting.line()));
    }

    /** A patch that sets the value, when the value is written inline and can be replaced exactly. */
    public Optional<Patch> replaceValue(Setting setting, String value) {
        if (setting.valueStart().isEmpty() || setting.valueEnd().isEmpty()) {
            return Optional.empty();
        }
        TextEdit edit =
                new TextEdit(setting.valueStart().getAsInt(), setting.valueEnd().getAsInt(), value);
        return Optional.of(Patches.unifiedDiff(source, List.of(edit)));
    }
}
