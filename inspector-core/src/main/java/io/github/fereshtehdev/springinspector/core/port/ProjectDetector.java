package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import java.nio.file.Path;

public interface ProjectDetector {

    DetectedProject detect(Path projectRoot);
}
