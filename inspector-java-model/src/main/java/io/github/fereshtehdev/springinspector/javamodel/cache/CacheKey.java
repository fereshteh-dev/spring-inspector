package io.github.fereshtehdev.springinspector.javamodel.cache;

import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import java.nio.file.Path;

/**
 * Identifies one check's result for one file.
 *
 * @param fingerprint the check's code and every input besides source files that can change its result
 * @param file the inspected file
 * @param contentHash hash of the file's content
 */
public record CacheKey(String fingerprint, Path file, String contentHash) {

    public CacheKey {
        file = ProjectPath.requireRelative(file);
    }
}
