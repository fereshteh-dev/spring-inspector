package io.github.fereshtehdev.springinspector.git;

import io.github.fereshtehdev.springinspector.core.domain.ProjectPath;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.core.port.SourceProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

final class WorkingTreeSources implements SourceProvider {

    private final Path projectRoot;

    WorkingTreeSources(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Override
    public Optional<SourceFile> read(Path projectRelativePath) {
        Path file = projectRoot.resolve(ProjectPath.requireRelative(projectRelativePath));
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().startsWith(projectRoot.toRealPath())
                    || Files.size(file) > SourceLimits.MAX_SOURCE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(
                    new SourceFile(projectRelativePath, new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + projectRelativePath, e);
        }
    }
}
