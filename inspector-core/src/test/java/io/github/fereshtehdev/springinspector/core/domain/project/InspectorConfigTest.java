package io.github.fereshtehdev.springinspector.core.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InspectorConfigTest {

    private static InspectorConfig config(Duration timeout, int maxFindings) {
        return new InspectorConfig(Set.of(), Set.of(), Map.of(), timeout, maxFindings, true, GatePolicy.defaults());
    }

    @Test
    void defaultsToChangedLinesBlockerGateAndFiveSecondTimeout() {
        InspectorConfig defaults = InspectorConfig.defaults();

        assertThat(defaults.changedLinesOnly()).isTrue();
        assertThat(defaults.checkTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.gatePolicy().blockOn()).isEqualTo(Severity.BLOCKER);
        assertThat(defaults.gatePolicy().minimumScore()).isEmpty();
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThatThrownBy(() -> config(Duration.ZERO, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(Duration.ofSeconds(-1), 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresShowingAtLeastOneFindingPerCategory() {
        assertThatThrownBy(() -> config(Duration.ofSeconds(1), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disablesByRuleOrCategory() {
        InspectorConfig config = new InspectorConfig(
                Set.of(Category.CLEAN_CODE),
                Set.of(RuleId.of("SEC-001")),
                Map.of(),
                Duration.ofSeconds(1),
                10,
                true,
                GatePolicy.defaults());

        assertThat(config.isEnabled(RuleId.of("SEC-001"), Category.SECURITY)).isFalse();
        assertThat(config.isEnabled(RuleId.of("CC-001"), Category.CLEAN_CODE)).isFalse();
        assertThat(config.isEnabled(RuleId.of("SEC-002"), Category.SECURITY)).isTrue();
    }

    @Test
    void acceptsMinimumScoresFromZeroToHundred() {
        assertThat(new GatePolicy(Severity.BLOCKER, OptionalInt.of(0)).minimumScore())
                .hasValue(0);
        assertThat(new GatePolicy(Severity.BLOCKER, OptionalInt.of(100)).minimumScore())
                .hasValue(100);
    }

    @Test
    void rejectsMinimumScoresOutsideZeroToHundred() {
        assertThatThrownBy(() -> new GatePolicy(Severity.BLOCKER, OptionalInt.of(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatePolicy(Severity.BLOCKER, OptionalInt.of(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesModulesDefensively() {
        List<Path> modules = new ArrayList<>(List.of(Path.of("app")));

        ProjectContext project = new ProjectContext(
                Path.of("."), DetectedVersions.none(), BuildTool.MAVEN, modules, InspectorConfig.defaults());
        modules.clear();

        assertThat(project.modules()).containsExactly(Path.of("app"));
    }
}
