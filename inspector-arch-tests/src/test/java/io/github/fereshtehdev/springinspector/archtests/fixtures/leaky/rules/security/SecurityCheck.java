package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.rules.security;

import io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.rules.spring.SpringCheck;

public final class SecurityCheck {

    public String delegate(SpringCheck other) {
        return other.id();
    }
}
