package io.github.fereshtehdev.springinspector.javamodel.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.core.domain.Finding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FindingCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final CacheKey KEY = new CacheKey("rule@jar", SampleFindings.FILE, "hash-1");
    private static final Path DEPENDENCY = Path.of("src", "main", "java", "Dep.java");
    private static final List<Finding> FINDINGS = List.of(SampleFindings.withPatch());

    @TempDir
    Path project;

    private final CacheSecret secret = CacheSecret.random();

    private static Optional<String> unchanged(Path path) {
        return Optional.of("dep-1");
    }

    private FindingCache open(Instant now) {
        return FindingCache.open(project, secret, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void findingsStoredInOneRunAreFoundInTheNext() {
        FindingCache first = open(NOW);
        first.store(KEY, FINDINGS, Map.of(DEPENDENCY, Optional.of("dep-1")));
        assertThat(first.save()).isTrue();

        assertThat(open(NOW).lookup(KEY, FindingCacheTest::unchanged)).contains(FINDINGS);
    }

    @Test
    void aChangedDependencyInvalidatesTheEntry() {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of(DEPENDENCY, Optional.of("dep-1")));

        assertThat(cache.lookup(KEY, path -> Optional.of("dep-2"))).isEmpty();
    }

    @Test
    void aFileThatAppearsWhereNoneWasInvalidatesTheEntry() {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of(DEPENDENCY, Optional.empty()));

        assertThat(cache.lookup(KEY, path -> Optional.empty())).contains(FINDINGS);
        assertThat(cache.lookup(KEY, path -> Optional.of("new"))).isEmpty();
    }

    @Test
    void aDifferentContentHashIsADifferentEntry() {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());

        assertThat(cache.lookup(new CacheKey("rule@jar", SampleFindings.FILE, "hash-2"), FindingCacheTest::unchanged))
                .isEmpty();
    }

    @Test
    void aCacheSignedByAnotherUserIsIgnored() {
        FindingCache mine = open(NOW);
        mine.store(KEY, FINDINGS, Map.of());
        mine.save();

        FindingCache theirs = FindingCache.open(project, CacheSecret.random(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(theirs.lookup(KEY, FindingCacheTest::unchanged)).isEmpty();
    }

    @Test
    void aTamperedCacheIsIgnored() throws IOException {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());
        cache.save();
        Path file = project.resolve(CacheStore.DIRECTORY).resolve(CacheStore.FILE_NAME);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 1;
        Files.write(file, bytes);

        assertThat(open(NOW).lookup(KEY, FindingCacheTest::unchanged)).isEmpty();
    }

    @Test
    void entriesUnusedForAMonthAreDroppedOnSave() {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());
        cache.save();

        FindingCache monthLater = open(NOW.plus(Duration.ofDays(FindingCache.MAX_IDLE_DAYS + 1)));
        CacheKey fresh = new CacheKey("rule@jar", SampleFindings.FILE, "hash-2");
        monthLater.store(fresh, FINDINGS, Map.of());
        monthLater.save();

        FindingCache reopened = open(NOW.plus(Duration.ofDays(FindingCache.MAX_IDLE_DAYS + 1)));
        assertThat(reopened.size()).isEqualTo(1);
        assertThat(reopened.lookup(fresh, FindingCacheTest::unchanged)).isPresent();
    }

    @Test
    void usingAnEntryKeepsItAlive() {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());
        cache.save();

        Instant twentyDaysLater = NOW.plus(Duration.ofDays(20));
        FindingCache later = open(twentyDaysLater);
        later.lookup(KEY, FindingCacheTest::unchanged);
        later.save();

        assertThat(open(twentyDaysLater.plus(Duration.ofDays(20))).lookup(KEY, FindingCacheTest::unchanged))
                .isPresent();
    }

    @Test
    void savingWithoutChangesDoesNotRewriteTheFile() throws IOException {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());
        cache.save();
        Path file = project.resolve(CacheStore.DIRECTORY).resolve(CacheStore.FILE_NAME);
        Files.delete(file);

        assertThat(cache.save()).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test
    void aDisabledCacheRemembersNothing() {
        FindingCache cache = FindingCache.disabled();
        cache.store(KEY, FINDINGS, Map.of());

        assertThat(cache.enabled()).isFalse();
        assertThat(cache.lookup(KEY, FindingCacheTest::unchanged)).isEmpty();
        assertThat(cache.save()).isFalse();
    }

    @Test
    void theCacheDirectoryIsIgnoredByGit() throws IOException {
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());
        cache.save();

        assertThat(Files.readString(project.resolve(CacheStore.DIRECTORY).resolve(".gitignore")))
                .isEqualTo("*\n");
    }

    @Test
    void aFileInPlaceOfTheCacheDirectoryDisablesSaving() throws IOException {
        Files.createDirectories(project.resolve(".spring-inspector"));
        Files.writeString(project.resolve(CacheStore.DIRECTORY), "not a directory");
        FindingCache cache = open(NOW);
        cache.store(KEY, FINDINGS, Map.of());

        assertThat(cache.save()).isFalse();
    }
}
