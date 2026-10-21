package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.rules.cleancode;

import org.assertj.core.api.Assertions;

public final class ThirdPartyCheck {

    public void verify(String value) {
        Assertions.assertThat(value).isNotBlank();
    }
}
