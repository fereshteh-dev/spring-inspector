package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.advisories.Advisory.AffectedPackage;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Metadata;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvisoryStoreTest {

    static final AdvisoryDatabase DATABASE = new AdvisoryDatabase(
            List.of(
                    new Advisory(
                            "GHSA-1",
                            List.of("CVE-2024-1", "GHSA-alias"),
                            "Ümlaut summary",
                            AdvisorySeverity.CRITICAL,
                            List.of(AdvisoryTest.SPRING_WEB)),
                    new Advisory(
                            "GHSA-2",
                            List.of(),
                            "Listed versions",
                            AdvisorySeverity.UNKNOWN,
                            List.of(
                                    new AffectedPackage(
                                            "g:a", List.of(AdvisoryTest.range("1.0", null, "1.4")), Set.of()),
                                    new AffectedPackage("g:b", List.of(), Set.of("1.0", "1.1"))))),
            new Metadata(Instant.parse("2026-10-20T08:00:00Z"), Optional.of("\"etag-1\"")));

    @TempDir
    Path home;

    @Test
    void readsBackWhatItSaved() {
        AdvisoryStore store = new AdvisoryStore(home.resolve("advisories"));

        store.save(DATABASE);

        AdvisoryDatabase loaded = store.load().orElseThrow();
        assertThat(loaded.advisories()).isEqualTo(DATABASE.advisories());
        assertThat(loaded.metadata()).isEqualTo(DATABASE.metadata());
        assertThat(store.file()).hasFileName(AdvisoryStore.FILE_NAME);
        assertThat(home.resolve("advisories")).isDirectoryContaining(path -> path.equals(store.file()));
        assertThat(home.resolve("advisories")).isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void keepsAMissingSourceVersionEmpty() {
        AdvisoryDatabase unversioned = new AdvisoryDatabase(List.of(), new Metadata(Instant.EPOCH, Optional.empty()));

        AdvisoryDatabase decoded = AdvisoryStore.decode(AdvisoryStore.encode(unversioned));

        assertThat(decoded.metadata().sourceVersion()).isEmpty();
        assertThat(decoded.advisories()).isEmpty();
    }

    @Test
    void replacesAnEarlierFile() {
        AdvisoryStore store = new AdvisoryStore(home);
        store.save(DATABASE);
        AdvisoryDatabase newer = new AdvisoryDatabase(List.of(), new Metadata(Instant.now(), Optional.of("2")));

        store.save(newer);

        assertThat(store.load())
                .hasValueSatisfying(loaded -> assertThat(loaded.advisories()).isEmpty());
    }

    @Test
    void treatsAMissingFileAsNoDatabase() {
        assertThat(new AdvisoryStore(home).load()).isEmpty();
    }

    @Test
    void treatsADamagedFileAsNoDatabase() throws IOException {
        AdvisoryStore store = new AdvisoryStore(home);
        byte[] valid = AdvisoryStore.encode(DATABASE);
        byte[] flipped = valid.clone();
        flipped[valid.length / 2] ^= 0x01;

        Files.write(store.file(), flipped);
        assertThat(store.load()).isEmpty();

        Files.write(store.file(), Arrays.copyOf(valid, valid.length - 20));
        assertThat(store.load()).isEmpty();

        Files.write(store.file(), new byte[] {1, 2, 3});
        assertThat(store.load()).isEmpty();
    }

    @Test
    void treatsAFileOfAnotherFormatAsNoDatabase() throws IOException {
        AdvisoryStore store = new AdvisoryStore(home);
        byte[] otherVersion = AdvisoryStore.encode(DATABASE);
        otherVersion[7] = 99;
        CRC32 crc = new CRC32();
        crc.update(otherVersion, 0, otherVersion.length - 8);
        ByteBuffer.wrap(otherVersion).putLong(otherVersion.length - 8, crc.getValue());

        Files.write(store.file(), otherVersion);

        assertThat(store.load()).isEmpty();
    }
}
