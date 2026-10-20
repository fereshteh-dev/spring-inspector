package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.core;

import io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.git.GitAdapter;

public final class AdapterAwareCore {

    public String describe(GitAdapter adapter) {
        return adapter.name();
    }
}
