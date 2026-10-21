package io.github.fereshtehdev.springinspector.advisories;

import io.github.fereshtehdev.springinspector.advisories.AdvisoryDatabase.Metadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Brings the local advisory database up to date from OSV's Maven archive. The archive is only downloaded when it
 * changed since the last update (by ETag), verified against the MD5 the storage service publishes, and read as
 * untrusted data. Nothing about the project is sent: the request is a plain download.
 */
public final class AdvisoryUpdater {

    public static final URI OSV_MAVEN = URI.create("https://osv-vulnerabilities.storage.googleapis.com/Maven/all.zip");

    private final AdvisoryStore store;
    private final Download download;
    private final Clock clock;

    public AdvisoryUpdater(AdvisoryStore store, Map<String, String> environment) {
        this(store, Download.http(environment), Clock.systemUTC());
    }

    AdvisoryUpdater(AdvisoryStore store, Download download, Clock clock) {
        this.store = store;
        this.download = download;
        this.clock = clock;
    }

    public Outcome update() {
        Optional<AdvisoryDatabase> current = store.load();
        Optional<String> knownVersion =
                current.flatMap(database -> database.metadata().sourceVersion());
        Download.Response response = download.get(OSV_MAVEN, knownVersion);
        if (response.notModified()) {
            AdvisoryDatabase unchanged = current.orElseThrow(
                    () -> new IllegalStateException("The server reported no change, but there is no local copy"));
            AdvisoryDatabase refreshed =
                    new AdvisoryDatabase(unchanged.advisories(), new Metadata(clock.instant(), knownVersion));
            store.save(refreshed);
            return new Outcome.UpToDate(refreshed.advisories().size());
        }
        verify(response);
        List<Advisory> advisories = OsvArchive.read(response.body());
        store.save(new AdvisoryDatabase(advisories, new Metadata(clock.instant(), response.version())));
        return new Outcome.Updated(advisories.size());
    }

    private static void verify(Download.Response response) {
        if (response.md5().isEmpty()) {
            return;
        }
        try {
            byte[] actual = MessageDigest.getInstance("MD5").digest(response.body());
            if (!MessageDigest.isEqual(
                    actual, Base64.getDecoder().decode(response.md5().get()))) {
                throw new IllegalStateException("The downloaded advisories do not match their published checksum");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required by every Java platform", e);
        }
    }

    /** What an update did. */
    public sealed interface Outcome {

        int advisories();

        record Updated(int advisories) implements Outcome {}

        record UpToDate(int advisories) implements Outcome {}
    }

    /** One HTTP download, behind an interface so tests run without a network. */
    interface Download {

        Response get(URI uri, Optional<String> knownVersion);

        /**
         * What the server answered. The body is only handed from the download to the parser, never compared.
         *
         * @param version the response's ETag, to send back next time
         * @param md5 the base64 MD5 the server published for the body, if any
         */
        @SuppressWarnings("ArrayRecordComponent")
        record Response(boolean notModified, byte[] body, Optional<String> version, Optional<String> md5) {}

        static Download http(Map<String, String> environment) {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .proxy(proxy(environment))
                    .build();
            return (uri, knownVersion) -> {
                HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMinutes(5))
                        .GET();
                knownVersion.ifPresent(etag -> request.header("If-None-Match", etag));
                try {
                    HttpResponse<byte[]> response =
                            client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == 304) {
                        return new Response(true, new byte[0], knownVersion, Optional.empty());
                    }
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("Downloading advisories failed: HTTP " + response.statusCode());
                    }
                    Optional<String> md5 = response.headers().allValues("x-goog-hash").stream()
                            .filter(hash -> hash.startsWith("md5="))
                            .map(hash -> hash.substring(4))
                            .findFirst();
                    return new Response(
                            false, response.body(), response.headers().firstValue("ETag"), md5);
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not reach " + uri.getHost() + ": " + e.getMessage(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while downloading advisories", e);
                }
            };
        }

        /** {@code HTTPS_PROXY} when set, otherwise Java's proxy settings such as {@code -Dhttps.proxyHost}. */
        static ProxySelector proxy(Map<String, String> environment) {
            String configured = environment.getOrDefault("HTTPS_PROXY", environment.getOrDefault("https_proxy", ""));
            if (configured.isBlank()) {
                return ProxySelector.getDefault();
            }
            URI proxy = URI.create(configured.contains("://") ? configured : "http://" + configured);
            return ProxySelector.of(
                    InetSocketAddress.createUnresolved(proxy.getHost(), proxy.getPort() < 0 ? 80 : proxy.getPort()));
        }
    }
}
