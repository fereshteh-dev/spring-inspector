package io.github.fereshtehdev.springinspector.advisories;

import java.util.Locale;

/** The severity the GitHub Advisory Database assigns after review. */
public enum AdvisorySeverity {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL,
    UNKNOWN;

    static AdvisorySeverity of(String text) {
        return switch (text.strip().toUpperCase(Locale.ROOT)) {
            case "LOW" -> LOW;
            case "MODERATE", "MEDIUM" -> MODERATE;
            case "HIGH" -> HIGH;
            case "CRITICAL" -> CRITICAL;
            default -> UNKNOWN;
        };
    }
}
