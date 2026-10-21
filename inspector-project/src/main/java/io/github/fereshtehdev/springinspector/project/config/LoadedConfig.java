package io.github.fereshtehdev.springinspector.project.config;

import io.github.fereshtehdev.springinspector.core.domain.project.InspectorConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The configuration to use, with what the user should know about the file it came from.
 *
 * @param file the file read, or empty when the defaults apply
 */
public record LoadedConfig(InspectorConfig config, List<String> warnings, Optional<Path> file) {

    public LoadedConfig {
        warnings = List.copyOf(warnings);
    }
}
