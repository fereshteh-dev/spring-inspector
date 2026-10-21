package io.github.fereshtehdev.springinspector.advisories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class OsvArchiveTest {

    static final String SPRING_ADVISORY = """
            {
              "id": "GHSA-g5vr-rgqm-vf78",
              "aliases": ["CVE-2024-38816"],
              "summary": "Path traversal vulnerability in functional web frameworks",
              "affected": [
                {
                  "package": {"ecosystem": "Maven", "name": "org.springframework:spring-webmvc"},
                  "ranges": [
                    {"type": "ECOSYSTEM", "events": [{"introduced": "0"}, {"fixed": "5.3.40"}]},
                    {"type": "ECOSYSTEM", "events": [{"introduced": "6.1.0"}, {"fixed": "6.1.13"}]}
                  ]
                },
                {"package": {"ecosystem": "npm", "name": "left-pad"}, "versions": ["1.0.0"]}
              ],
              "database_specific": {"severity": "HIGH"}
            }
            """;

    @Test
    void readsMavenPackagesRangesAndSeverity() {
        Advisory advisory = OsvArchive.parse(bytes(SPRING_ADVISORY)).orElseThrow();

        assertThat(advisory.id()).isEqualTo("GHSA-g5vr-rgqm-vf78");
        assertThat(advisory.aliases()).containsExactly("CVE-2024-38816");
        assertThat(advisory.summary()).isEqualTo("Path traversal vulnerability in functional web frameworks");
        assertThat(advisory.severity()).isEqualTo(AdvisorySeverity.HIGH);
        assertThat(advisory.packages()).singleElement().satisfies(affected -> {
            assertThat(affected.coordinate()).isEqualTo("org.springframework:spring-webmvc");
            assertThat(affected.ranges())
                    .containsExactly(
                            new AffectedRange(Optional.empty(), Optional.of("5.3.40"), Optional.empty()),
                            new AffectedRange(Optional.of("6.1.0"), Optional.of("6.1.13"), Optional.empty()));
            assertThat(affected.versions()).isEmpty();
        });
    }

    @Test
    void closesRangesWithLastAffectedAndLeavesUnfinishedRangesOpen() {
        Advisory advisory = OsvArchive.parse(bytes("""
                        {"id": "GHSA-1", "affected": [{
                          "package": {"ecosystem": "Maven", "name": "g:a"},
                          "ranges": [{"type": "ECOSYSTEM", "events": [
                            {"introduced": "1.0"}, {"last_affected": "1.4"},
                            {"fixed": "9.9"},
                            {"introduced": "2.0"}
                          ]}]
                        }]}
                        """)).orElseThrow();

        assertThat(advisory.packages().getFirst().ranges())
                .containsExactly(
                        new AffectedRange(Optional.of("1.0"), Optional.empty(), Optional.of("1.4")),
                        new AffectedRange(Optional.of("2.0"), Optional.empty(), Optional.empty()));
        assertThat(advisory.severity()).isEqualTo(AdvisorySeverity.UNKNOWN);
    }

    @Test
    void usesListedVersionsOnlyWhenThereAreNoRanges() {
        Advisory advisory = OsvArchive.parse(bytes("""
                        {"id": "GHSA-2", "affected": [{
                          "package": {"ecosystem": "Maven", "name": "g:a"},
                          "versions": ["1.0", "1.1"]
                        }]}
                        """)).orElseThrow();

        assertThat(advisory.packages().getFirst().versions()).containsExactlyInAnyOrder("1.0", "1.1");
    }

    @Test
    void takesTheSummaryFromTheDetailsWhenThereIsNone() {
        String longLine = "x".repeat(300);
        Advisory fromDetails = OsvArchive.parse(bytes("""
                        {"id": "GHSA-3", "details": "\\n  First line.\\nSecond line.",
                         "affected": [{"package": {"ecosystem": "Maven", "name": "g:a"}, "versions": ["1"]}]}
                        """)).orElseThrow();
        Advisory truncated = OsvArchive.parse(bytes("""
                        {"id": "GHSA-4", "details": "%s",
                         "affected": [{"package": {"ecosystem": "Maven", "name": "g:a"}, "versions": ["1"]}]}
                        """.formatted(longLine))).orElseThrow();
        Advisory bare = OsvArchive.parse(bytes("""
                        {"id": "GHSA-5", "affected": [{"package": {"ecosystem": "Maven", "name": "g:a"}, "versions": ["1"]}]}
                        """)).orElseThrow();

        assertThat(fromDetails.summary()).isEqualTo("First line.");
        assertThat(truncated.summary()).hasSize(201).endsWith("…");
        assertThat(bare.summary()).isEqualTo("GHSA-5");
    }

    @Test
    void skipsWithdrawnForeignAndUnreadableEntries() {
        assertThat(OsvArchive.parse(bytes("""
                        {"id": "GHSA-6", "withdrawn": "2024-01-01T00:00:00Z",
                         "affected": [{"package": {"ecosystem": "Maven", "name": "g:a"}, "versions": ["1"]}]}
                        """))).isEmpty();
        assertThat(OsvArchive.parse(bytes("""
                        {"id": "PYSEC-1", "affected": [{"package": {"ecosystem": "PyPI", "name": "django"}}]}
                        """))).isEmpty();
        assertThat(OsvArchive.parse(bytes("{\"affected\": []}"))).isEmpty();
        assertThat(OsvArchive.parse(bytes("{ not json"))).isEmpty();
    }

    @Test
    void readsEveryJsonDocumentInTheArchive() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("GHSA-g5vr-rgqm-vf78.json", SPRING_ADVISORY);
        entries.put("README.txt", "not an advisory");
        entries.put("broken.json", "{");

        List<Advisory> advisories = OsvArchive.read(zip(entries));

        assertThat(advisories).extracting(Advisory::id).containsExactly("GHSA-g5vr-rgqm-vf78");
    }

    @Test
    void refusesOversizedEntries() {
        byte[] bomb = zip(Map.of("big.json", " ".repeat((int) OsvArchive.MAX_ENTRY_BYTES + 1)));

        assertThatThrownBy(() -> OsvArchive.read(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void rejectsDataThatIsNotAnOsvArchive() {
        byte[] errorPage = bytes("<html>Service unavailable</html>");
        byte[] emptyZip = zip(Map.of("README.txt", "nothing here"));

        assertThatThrownBy(() -> OsvArchive.read(errorPage)).hasMessageContaining("Not an OSV archive");
        assertThatThrownBy(() -> OsvArchive.read(emptyZip)).hasMessageContaining("Not an OSV archive");
    }

    static byte[] zip(Map<String, String> entries) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(bytes(entry.getValue()));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
