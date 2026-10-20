package io.github.fereshtehdev.springinspector.core.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectedProjectTest {

    @Test
    void becomesAProjectContextOnceConfigIsKnown() {
        DetectedProject detected = new DetectedProject(
                Path.of("repo"), DetectedVersions.none(), BuildTool.GRADLE, List.of(Path.of("api")), List.of());
        InspectorConfig config = InspectorConfig.defaults();

        ProjectContext context = detected.withConfig(config);

        assertThat(context)
                .isEqualTo(new ProjectContext(
                        Path.of("repo"), DetectedVersions.none(), BuildTool.GRADLE, List.of(Path.of("api")), config));
    }

    @Test
    void copiesWarningsDefensively() {
        List<String> warnings = new ArrayList<>(List.of("pom.xml could not be read"));

        DetectedProject detected =
                new DetectedProject(Path.of("repo"), DetectedVersions.none(), BuildTool.MAVEN, List.of(), warnings);
        warnings.clear();

        assertThat(detected.warnings()).containsExactly("pom.xml could not be read");
    }
}
