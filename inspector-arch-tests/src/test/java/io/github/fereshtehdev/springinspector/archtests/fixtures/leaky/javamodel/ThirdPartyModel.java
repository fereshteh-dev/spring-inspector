package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.javamodel;

import org.assertj.core.api.Assertions;

public final class ThirdPartyModel {

    public void verify(String value) {
        Assertions.assertThat(value).isNotBlank();
    }
}
