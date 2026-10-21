package io.github.fereshtehdev.springinspector.javamodel.cache;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @param dependencies other files the result depends on, with the content hash they had, or empty when the result
 *     depends on the file being absent
 */
record CacheEntry(List<Finding> findings, Map<Path, Optional<String>> dependencies, long lastUsedDay) {

    CacheEntry {
        findings = List.copyOf(findings);
        dependencies = Map.copyOf(dependencies);
    }

    CacheEntry usedOn(long day) {
        return new CacheEntry(findings, dependencies, day);
    }
}
