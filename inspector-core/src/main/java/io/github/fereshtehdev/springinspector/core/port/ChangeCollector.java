package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeScope;

public interface ChangeCollector {

    CollectedChanges collect(ChangeScope scope);
}
