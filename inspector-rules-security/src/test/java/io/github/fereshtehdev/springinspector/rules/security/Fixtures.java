package io.github.fereshtehdev.springinspector.rules.security;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;

/** Fixture loading for this module's rule tests. */
final class Fixtures {

    /**
     * Token-shaped test values are assembled here rather than stored in fixtures, so the repository never contains
     * a string that secret scanners take for a live key.
     */
    static final String STRIPE_KEY = "sk_" + "live_" + "FAKE".repeat(5) + "1234";

    static final String AWS_KEY = "AKIA" + "FAKE".repeat(4);

    private Fixtures() {}

    static String fixture(String rule, String path) {
        return CheckTester.resource("fixtures/" + rule + "/" + path)
                .replace("@STRIPE_KEY@", STRIPE_KEY)
                .replace("@AWS_KEY@", AWS_KEY);
    }

    static List<Integer> lines(List<Finding> findings) {
        return findings.stream()
                .map(finding -> finding.location().lines().start())
                .toList();
    }
}
