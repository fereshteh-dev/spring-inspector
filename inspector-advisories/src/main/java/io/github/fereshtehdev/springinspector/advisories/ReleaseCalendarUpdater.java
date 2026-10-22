package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Download;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar.ReleaseLine;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Downloads the release calendar: support dates and newest releases of Spring Boot (endoflife.date), Java as Eclipse
 * Temurin ships it (endoflife.date), and Spring AI (Maven Central). Nothing about the project is sent.
 */
public final class ReleaseCalendarUpdater {

    static final Map<String, URI> SOURCES = Map.of(
            ReleaseCalendar.SPRING_BOOT, URI.create("https://endoflife.date/api/spring-boot.json"),
            ReleaseCalendar.JAVA, URI.create("https://endoflife.date/api/eclipse-temurin.json"),
            ReleaseCalendar.SPRING_AI,
                    URI.create(
                            "https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml"));

    private final ReleaseCalendarStore store;
    private final Download download;
    private final Clock clock;

    public ReleaseCalendarUpdater(ReleaseCalendarStore store, Map<String, String> environment) {
        this(store, Download.http(environment), Clock.systemUTC());
    }

    ReleaseCalendarUpdater(ReleaseCalendarStore store, Download download, Clock clock) {
        this.store = store;
        this.download = download;
        this.clock = clock;
    }

    /** Downloads the calendar unless the stored one is younger than {@code maxAge}; returns the products updated. */
    public int updateIfOlderThan(Duration maxAge) {
        Optional<ReleaseCalendar> current = store.load();
        if (current.isPresent()
                && current.get().fetchedAt().isAfter(clock.instant().minus(maxAge))) {
            return 0;
        }
        return update();
    }

    /** Downloads every product; a product that fails keeps its stored lines. Returns the products updated. */
    public int update() {
        Map<String, List<ReleaseLine>> products =
                new LinkedHashMap<>(store.load().map(ReleaseCalendar::products).orElse(Map.of()));
        int updated = 0;
        RuntimeException firstFailure = null;
        for (Map.Entry<String, URI> source : SOURCES.entrySet()) {
            Function<byte[], List<ReleaseLine>> parser = source.getKey().equals(ReleaseCalendar.SPRING_AI)
                    ? ReleaseFacts::fromMavenMetadata
                    : ReleaseFacts::fromEndOfLife;
            try {
                products.put(
                        source.getKey(),
                        parser.apply(download.get(source.getValue(), Optional.empty())
                                .body()));
                updated++;
            } catch (RuntimeException failure) {
                firstFailure = firstFailure == null ? failure : firstFailure;
            }
        }
        if (updated == 0 && firstFailure != null) {
            throw firstFailure;
        }
        store.save(new ReleaseCalendar(products, clock.instant()));
        return updated;
    }
}
