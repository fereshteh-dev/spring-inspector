package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Download;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar;
import io.github.fereshtehdev.springinspector.core.domain.release.ReleaseCalendar.ReleaseLine;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseCalendarTest {

    static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
    static final String SPRING_BOOT = """
            [{"cycle":"4.1","releaseDate":"2026-06-30","eol":"2027-07-31","latest":"4.1.1","lts":false},
             {"cycle":"2.0","releaseDate":"2018-03-01","eol":true,"latest":"2.0.9"},
             {"cycle":"9.9","releaseDate":"soon","eol":false},
             {"cycle":""}]
            """;
    static final String TEMURIN = """
            [{"cycle":"25","releaseDate":"2025-09-22","eol":"2031-09-30","latest":"25.0.4.1+1"}]
            """;
    static final String SPRING_AI = """
            <metadata><versioning><versions>
              <version>1.0.0</version><version>1.0.9</version><version>1.0.10</version>
              <version>2.0.0-M8</version><version>2.0.0-RC2</version><version>2.0.0</version><version>2.0.1</version>
              <version>2.1.0-M1</version>
            </versions></versioning></metadata>
            """;

    @TempDir
    Path home;

    final List<URI> requested = new ArrayList<>();

    @Test
    void readsEndOfLifeDataAndIgnoresWhatItCannotUse() {
        List<ReleaseLine> lines = ReleaseFacts.fromEndOfLife(bytes(SPRING_BOOT));

        assertThat(lines)
                .containsExactly(
                        new ReleaseLine(
                                "4.1",
                                Optional.of(LocalDate.of(2026, 6, 30)),
                                Optional.of(LocalDate.of(2027, 7, 31)),
                                Optional.of("4.1.1")),
                        new ReleaseLine(
                                "2.0", Optional.of(LocalDate.of(2018, 3, 1)), Optional.empty(), Optional.of("2.0.9")),
                        new ReleaseLine("9.9", Optional.empty(), Optional.empty(), Optional.empty()));
        assertThatThrownBy(() -> ReleaseFacts.fromEndOfLife(bytes("{ not json")))
                .hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> ReleaseFacts.fromEndOfLife(bytes("[]"))).hasMessageContaining("no release lines");
        assertThatThrownBy(() -> ReleaseFacts.fromEndOfLife(new byte[ReleaseFacts.MAX_BYTES + 1]))
                .hasMessageContaining("larger than");
    }

    @Test
    void readsTheNewestFinalReleaseOfEachLineFromMavenCentral() {
        assertThat(ReleaseFacts.fromMavenMetadata(bytes(SPRING_AI)))
                .extracting(ReleaseLine::line, line -> line.latest().orElseThrow())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("1.0", "1.0.10"),
                        org.assertj.core.groups.Tuple.tuple("2.0", "2.0.1"));
        assertThatThrownBy(() -> ReleaseFacts.fromMavenMetadata(bytes("<metadata/>")))
                .hasMessageContaining("no final releases");
        assertThatThrownBy(() -> ReleaseFacts.fromMavenMetadata(new byte[ReleaseFacts.MAX_BYTES + 1]))
                .hasMessageContaining("larger than");
    }

    @Test
    void downloadsEveryProductAndStoresTheCalendar() {
        ReleaseCalendarStore store = new ReleaseCalendarStore(home);

        int updated = updater(store, this::serve).update();

        assertThat(updated).isEqualTo(3);
        assertThat(requested).containsExactlyInAnyOrderElementsOf(ReleaseCalendarUpdater.SOURCES.values());
        ReleaseCalendar calendar = store.load().orElseThrow();
        assertThat(calendar.fetchedAt()).isEqualTo(NOW);
        assertThat(calendar.line(ReleaseCalendar.SPRING_BOOT, "4.1")).isPresent();
        assertThat(calendar.line(ReleaseCalendar.JAVA, "25")).isPresent();
        assertThat(calendar.line(ReleaseCalendar.SPRING_AI, "2.0"))
                .hasValueSatisfying(line -> assertThat(line.latest()).contains("2.0.1"));
    }

    @Test
    void keepsTheStoredLinesOfAProductThatFails() {
        ReleaseCalendarStore store = new ReleaseCalendarStore(home);
        updater(store, this::serve).update();

        int updated = updater(store, (uri, known) -> {
                    if (uri.toString().contains("eclipse-temurin")) {
                        throw new IllegalStateException("HTTP 503");
                    }
                    return serve(uri, known);
                })
                .update();

        assertThat(updated).isEqualTo(2);
        assertThat(store.load().orElseThrow().line(ReleaseCalendar.JAVA, "25")).isPresent();
    }

    @Test
    void failsOnlyWhenNothingCouldBeDownloaded() {
        Download offline = (uri, known) -> {
            throw new IllegalStateException("offline");
        };

        assertThatThrownBy(
                        () -> updater(new ReleaseCalendarStore(home), offline).update())
                .hasMessage("offline");
    }

    @Test
    void skipsARecentCalendar() {
        ReleaseCalendarStore store = new ReleaseCalendarStore(home);
        updater(store, this::serve).update();
        requested.clear();

        assertThat(updater(store, this::serve).updateIfOlderThan(Duration.ofDays(1)))
                .isZero();
        assertThat(requested).isEmpty();
        assertThat(new ReleaseCalendarUpdater(
                                store, this::serve, Clock.fixed(NOW.plus(Duration.ofDays(2)), ZoneOffset.UTC))
                        .updateIfOlderThan(Duration.ofDays(1)))
                .isEqualTo(3);
    }

    @Test
    void readsADamagedOrMissingCalendarAsNone() throws IOException {
        ReleaseCalendarStore store = new ReleaseCalendarStore(home);
        assertThat(store.load()).isEmpty();

        Files.writeString(store.file(), "{ damaged");

        assertThat(store.load()).isEmpty();
    }

    @Test
    void roundTripsLinesWithoutDates() {
        ReleaseCalendarStore store = new ReleaseCalendarStore(home);
        ReleaseCalendar calendar = new ReleaseCalendar(
                Map.of("x", List.of(new ReleaseLine("1.0", Optional.empty(), Optional.empty(), Optional.empty()))),
                NOW);

        store.save(calendar);

        assertThat(store.load()).contains(calendar);
    }

    ReleaseCalendarUpdater updater(ReleaseCalendarStore store, Download download) {
        return new ReleaseCalendarUpdater(store, download, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    Download.Response serve(URI uri, Optional<String> known) {
        requested.add(uri);
        String body = uri.toString().contains("spring-boot")
                ? SPRING_BOOT
                : uri.toString().contains("eclipse-temurin") ? TEMURIN : SPRING_AI;
        return new Download.Response(false, bytes(body), Optional.empty(), Optional.empty());
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
