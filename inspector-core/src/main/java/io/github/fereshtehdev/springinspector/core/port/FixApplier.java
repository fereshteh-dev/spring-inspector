package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.Patch;
import java.nio.file.Path;

public interface FixApplier {

    FixResult apply(Path projectRoot, Patch patch);
}
