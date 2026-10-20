package io.github.fereshtehdev.springinspector.project;

import io.github.fereshtehdev.springinspector.core.domain.project.BuildTool;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedProject;
import io.github.fereshtehdev.springinspector.core.domain.project.DetectedVersions;
import io.github.fereshtehdev.springinspector.core.port.ProjectDetector;
import io.github.fereshtehdev.springinspector.project.gradle.GradleDetector;
import io.github.fereshtehdev.springinspector.project.maven.MavenDetector;
import java.nio.file.Path;

public final class BuildFileProjectDetector implements ProjectDetector {

    private static final Path JAVA_VERSION_FILE = Path.of(".java-version");

    @Override
    public DetectedProject detect(Path projectRoot) {
        BuildFileReader reader = new BuildFileReader(projectRoot);
        boolean maven = reader.exists(MavenDetector.POM);
        boolean gradle = GradleDetector.MARKER_FILES.stream().anyMatch(reader::exists);
        if (maven && gradle) {
            reader.warn("Both Maven and Gradle build files found; using Maven");
        }
        BuildTool tool = maven ? BuildTool.MAVEN : gradle ? BuildTool.GRADLE : BuildTool.UNKNOWN;
        BuildFacts facts = switch (tool) {
            case MAVEN -> MavenDetector.detect(reader);
            case GRADLE -> GradleDetector.detect(reader);
            case UNKNOWN -> BuildFacts.none();
        };
        DetectedVersions versions = new DetectedVersions(
                facts.java().or(() -> reader.read(JAVA_VERSION_FILE).flatMap(JavaVersions::parse)),
                facts.springBoot(),
                facts.springAi());
        return new DetectedProject(projectRoot, versions, tool, facts.modules(), reader.warnings());
    }
}
