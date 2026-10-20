package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.rules.cleancode;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.port.Check;
import io.github.fereshtehdev.springinspector.core.port.InspectionInput;
import java.util.List;

public final class StandaloneCheck implements Check {

    @Override
    public RuleId id() {
        return RuleId.of("CC-STANDALONE");
    }

    @Override
    public Category category() {
        return Category.CLEAN_CODE;
    }

    @Override
    public List<Finding> inspect(InspectionInput input) {
        return List.of();
    }
}
