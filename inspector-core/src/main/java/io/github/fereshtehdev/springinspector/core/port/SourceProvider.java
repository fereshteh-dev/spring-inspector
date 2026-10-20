package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import java.nio.file.Path;
import java.util.Optional;

public interface SourceProvider {

    Optional<SourceFile> read(Path projectRelativePath);
}
