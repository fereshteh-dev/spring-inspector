package io.github.fereshtehdev.springinspector.core.port;

import io.github.fereshtehdev.springinspector.core.domain.change.ChangeSet;

public record CollectedChanges(ChangeSet changeSet, SourceProvider sources) {}
