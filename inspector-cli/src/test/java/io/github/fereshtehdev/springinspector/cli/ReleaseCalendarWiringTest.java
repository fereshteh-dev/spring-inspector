package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.port.Check;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReleaseCalendarWiringTest {

    @Test
    void givesTheCalendarToTheRulesThatUseItAndKeepsEveryOtherRule() {
        List<Check> registered = SpringInspector.checks();

        List<Check> wired = SpringInspector.withReleaseCalendar(registered, Optional::empty);

        assertThat(ids(wired)).isEqualTo(ids(registered));
        assertThat(ids(wired))
                .contains(
                        RuleId.of("migration.spring-boot-support"),
                        RuleId.of("migration.newer-patch"),
                        RuleId.of("migration.java-support"));
        for (int index = 0; index < registered.size(); index++) {
            boolean usesCalendar = Set.of(
                            "migration.spring-boot-support", "migration.newer-patch", "migration.java-support")
                    .contains(registered.get(index).id().value());
            if (usesCalendar) {
                assertThat(wired.get(index)).isNotSameAs(registered.get(index));
            } else {
                assertThat(wired.get(index)).isSameAs(registered.get(index));
            }
        }
    }

    private static Set<RuleId> ids(List<Check> checks) {
        return checks.stream().map(Check::id).collect(Collectors.toSet());
    }
}
