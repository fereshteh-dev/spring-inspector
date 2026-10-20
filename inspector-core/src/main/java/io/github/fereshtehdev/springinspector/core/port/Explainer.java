package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import java.util.List;
import java.util.Optional;

public interface Explainer {

    Optional<String> explain(Finding finding);

    List<Finding> findDesignIssues(InspectionInput input);
}
