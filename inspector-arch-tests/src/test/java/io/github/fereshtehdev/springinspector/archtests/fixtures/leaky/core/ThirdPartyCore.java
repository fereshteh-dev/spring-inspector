package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.core;

import org.assertj.core.api.Assertions;

public final class ThirdPartyCore {

    public void verify(String value) {
        Assertions.assertThat(value).isNotBlank();
    }
}
