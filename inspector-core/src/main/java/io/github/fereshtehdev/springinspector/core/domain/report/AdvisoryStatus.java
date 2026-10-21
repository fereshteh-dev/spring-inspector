package io.github.fereshtehdev.springinspector.core.domain.report;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * How current the local vulnerability advisories were when the report was made. New vulnerabilities are published
 * every day, so a clean result means little when the data is old.
 *
 * @param updatedAt when the advisories were last updated; empty when there are none on this machine
 * @param reportedAt when the report was made
 */
public record AdvisoryStatus(Optional<Instant> updatedAt, Instant reportedAt) {

    public static final Duration MAX_AGE = Duration.ofDays(7);

    public boolean isStale() {
        return updatedAt
                .map(updated -> updated.isBefore(reportedAt.minus(MAX_AGE)))
                .orElse(true);
    }

    /** What to tell the user, when the advisories are missing or older than {@link #MAX_AGE}. */
    public Optional<String> warning() {
        if (updatedAt.isEmpty()) {
            return Optional.of("No vulnerability advisories on this machine, so dependencies were not checked;"
                    + " run 'spring-inspector update'");
        }
        if (!isStale()) {
            return Optional.empty();
        }
        long days = Duration.between(updatedAt.get(), reportedAt).toDays();
        return Optional.of("The vulnerability advisories are " + days + " days old and may miss recent"
                + " vulnerabilities; run 'spring-inspector update'");
    }
}
