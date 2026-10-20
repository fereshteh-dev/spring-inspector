package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import java.util.List;

public interface Check {

    RuleId id();

    Category category();

    default Applicability applicability() {
        return Applicability.always();
    }

    List<Finding> inspect(InspectionInput input);
}
