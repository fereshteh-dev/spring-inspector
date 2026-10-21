package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Download;
import io.github.fereshtehdev.springinspector.advisories.AdvisoryUpdater.Outcome;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvisoryUpdaterTest {

    static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");
    static final byte[] ARCHIVE =
            OsvArchiveTest.zip(Map.of("GHSA-g5vr-rgqm-vf78.json", OsvArchiveTest.SPRING_ADVISORY));

    @TempDir
    Path home;

    final List<Optional<String>> requests = new ArrayList<>();

    @Test
    void downloadsAndStoresTheArchiveWithItsVersion() {
        AdvisoryStore store = new AdvisoryStore(home);

        Outcome outcome = updater(store, ok(ARCHIVE, Optional.of(md5(ARCHIVE)))).update();

        assertThat(outcome).isEqualTo(new Outcome.Updated(1));
        assertThat(requests).containsExactly(Optional.empty());
        AdvisoryDatabase saved = store.load().orElseThrow();
        assertThat(saved.metadata().fetchedAt()).isEqualTo(NOW);
        assertThat(saved.metadata().sourceVersion()).contains("\"etag-2\"");
        assertThat(saved.affecting("org.springframework:spring-webmvc", "6.1.5"))
                .hasSize(1);
    }

    @Test
    void asksOnlyForChangesAndKeepsTheDataWhenNothingChanged() {
        AdvisoryStore store = new AdvisoryStore(home);
        store.save(AdvisoryStoreTest.DATABASE);

        Outcome outcome = updater(store, (uri, known) -> {
                    requests.add(known);
                    return new Download.Response(true, new byte[0], known, Optional.empty());
                })
                .update();

        assertThat(outcome).isEqualTo(new Outcome.UpToDate(2));
        assertThat(requests).containsExactly(Optional.of("\"etag-1\""));
        AdvisoryDatabase saved = store.load().orElseThrow();
        assertThat(saved.advisories()).isEqualTo(AdvisoryStoreTest.DATABASE.advisories());
        assertThat(saved.metadata().fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesANotModifiedAnswerWithoutALocalCopy() {
        AdvisoryUpdater updater = updater(
                new AdvisoryStore(home),
                (uri, known) -> new Download.Response(true, new byte[0], known, Optional.empty()));

        assertThatThrownBy(updater::update).hasMessageContaining("no local copy");
    }

    @Test
    void rejectsADownloadThatDoesNotMatchItsChecksumAndKeepsTheOldData() {
        AdvisoryStore store = new AdvisoryStore(home);
        AdvisoryUpdater updater = updater(store, ok(ARCHIVE, Optional.of(md5(new byte[] {1}))));

        assertThatThrownBy(updater::update).hasMessageContaining("checksum");
        assertThat(store.load()).isEmpty();
    }

    @Test
    void acceptsADownloadWithoutAPublishedChecksum() {
        AdvisoryStore store = new AdvisoryStore(home);

        assertThat(updater(store, ok(ARCHIVE, Optional.empty())).update()).isEqualTo(new Outcome.Updated(1));
    }

    @Test
    void usesTheHttpsProxyFromTheEnvironment() {
        URI target = AdvisoryUpdater.OSV_MAVEN;

        assertThat(Download.proxy(Map.of("HTTPS_PROXY", "http://proxy.example:3128"))
                        .select(target))
                .containsExactly(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example", 3128)));
        assertThat(Download.proxy(Map.of("https_proxy", "proxy.example")).select(target))
                .singleElement()
                .satisfies(proxy -> assertThat(((InetSocketAddress) proxy.address()).getPort())
                        .isEqualTo(80));
    }

    @Test
    void fallsBackToJavasProxySettings() {
        assertThat(Download.proxy(Map.of())).isSameAs(ProxySelector.getDefault());
    }

    AdvisoryUpdater updater(AdvisoryStore store, Download download) {
        return new AdvisoryUpdater(store, download, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    Download ok(byte[] body, Optional<String> md5) {
        return (uri, known) -> {
            assertThat(uri).isEqualTo(AdvisoryUpdater.OSV_MAVEN);
            requests.add(known);
            return new Download.Response(false, body, Optional.of("\"etag-2\""), md5);
        };
    }

    static String md5(byte[] data) {
        try {
            return Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("MD5").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
