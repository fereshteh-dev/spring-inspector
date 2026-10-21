package io.github.fereshtehdev.springinspector.javamodel.cache;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.FindingSource;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.core.domain.Location;
import io.github.fereshtehdev.springinspector.core.domain.Patch;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhatsWrong;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A plain binary format: no Java serialization, so a tampered file can only fail to decode. Every distinct string is
 * stored once in a table and referenced by index, because fingerprints, contexts and dependency paths repeat across
 * thousands of entries.
 */
final class CacheCodec {

    private static final int MAGIC = 0x53494346;
    private static final int FORMAT_VERSION = 2;

    private CacheCodec() {}

    static byte[] encode(Map<CacheKey, CacheEntry> entries) {
        try {
            Encoder encoder = new Encoder();
            encoder.out.writeInt(entries.size());
            for (Map.Entry<CacheKey, CacheEntry> entry : entries.entrySet()) {
                encoder.key(entry.getKey());
                encoder.entry(entry.getValue());
            }
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(file);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(encoder.strings.size());
            for (String string : encoder.strings.keySet()) {
                byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                out.writeInt(bytes.length);
                out.write(bytes);
            }
            encoder.body.writeTo(out);
            out.flush();
            return file.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Throws {@link IllegalArgumentException} for anything that is not a well-formed cache. */
    static Map<CacheKey, CacheEntry> decode(byte[] data) {
        try {
            ByteBuffer in = ByteBuffer.wrap(data);
            if (in.getInt() != MAGIC || in.getInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Not a findings cache of this version");
            }
            Decoder decoder = new Decoder(in);
            int count = Decoder.count(in);
            Map<CacheKey, CacheEntry> entries = HashMap.newHashMap(count);
            for (int i = 0; i < count; i++) {
                entries.put(decoder.key(), decoder.entry());
            }
            if (in.hasRemaining()) {
                throw new IllegalArgumentException("Trailing bytes after the last entry");
            }
            return entries;
        } catch (BufferUnderflowException | IllegalStateException e) {
            throw new IllegalArgumentException("Truncated findings cache", e);
        }
    }

    private static final class Encoder {

        private final Map<String, Integer> strings = new LinkedHashMap<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(body);

        void key(CacheKey key) throws IOException {
            string(key.fingerprint());
            path(key.file());
            string(key.contentHash());
        }

        void entry(CacheEntry entry) throws IOException {
            out.writeLong(entry.lastUsedDay());
            out.writeInt(entry.dependencies().size());
            for (Map.Entry<Path, Optional<String>> dependency :
                    entry.dependencies().entrySet()) {
                path(dependency.getKey());
                optional(dependency.getValue());
            }
            out.writeInt(entry.findings().size());
            for (Finding finding : entry.findings()) {
                finding(finding);
            }
        }

        private void finding(Finding finding) throws IOException {
            string(finding.ruleId().value());
            string(finding.category().name());
            string(finding.severity().name());
            path(finding.location().file());
            out.writeInt(finding.location().lines().start());
            out.writeInt(finding.location().lines().end());
            string(finding.whatsWrong().description());
            string(finding.whatsWrong().snippet());
            string(finding.whyItMatters().explanation());
            out.writeInt(finding.whyItMatters().references().size());
            for (Reference reference : finding.whyItMatters().references()) {
                string(reference.label());
                string(reference.uri().toString());
            }
            string(finding.howToFix().explanation());
            optional(finding.howToFix().patch().map(Patch::unifiedDiff));
            string(finding.confidence().name());
            string(finding.source().name());
        }

        private void optional(Optional<String> value) throws IOException {
            out.writeBoolean(value.isPresent());
            if (value.isPresent()) {
                string(value.get());
            }
        }

        private void path(Path path) throws IOException {
            string(path.toString().replace('\\', '/'));
        }

        private void string(String value) throws IOException {
            Integer index = strings.get(value);
            if (index == null) {
                index = strings.size();
                strings.put(value, index);
            }
            out.writeInt(index);
        }
    }

    private static final class Decoder {

        private final ByteBuffer in;
        private final String[] strings;

        Decoder(ByteBuffer in) {
            this.in = in;
            this.strings = new String[count(in)];
            for (int i = 0; i < strings.length; i++) {
                byte[] bytes = new byte[count(in)];
                in.get(bytes);
                strings[i] = new String(bytes, StandardCharsets.UTF_8);
            }
        }

        CacheKey key() {
            return new CacheKey(string(), path(), string());
        }

        CacheEntry entry() {
            long lastUsedDay = in.getLong();
            int dependencyCount = count(in);
            Map<Path, Optional<String>> dependencies = HashMap.newHashMap(dependencyCount);
            for (int i = 0; i < dependencyCount; i++) {
                dependencies.put(path(), optional());
            }
            int findingCount = count(in);
            List<Finding> findings = new ArrayList<>(findingCount);
            for (int i = 0; i < findingCount; i++) {
                findings.add(finding());
            }
            return new CacheEntry(findings, dependencies, lastUsedDay);
        }

        private Finding finding() {
            RuleId ruleId = RuleId.of(string());
            Category category = Category.valueOf(string());
            Severity severity = Severity.valueOf(string());
            Location location = new Location(path(), LineRange.of(in.getInt(), in.getInt()));
            WhatsWrong whatsWrong = new WhatsWrong(string(), string());
            String explanation = string();
            int referenceCount = count(in);
            List<Reference> references = new ArrayList<>(referenceCount);
            for (int i = 0; i < referenceCount; i++) {
                references.add(new Reference(string(), URI.create(string())));
            }
            HowToFix howToFix = new HowToFix(string(), optional().map(Patch::new));
            return new Finding(
                    ruleId,
                    category,
                    severity,
                    location,
                    whatsWrong,
                    new WhyItMatters(explanation, references),
                    howToFix,
                    Confidence.valueOf(string()),
                    FindingSource.valueOf(string()));
        }

        private Optional<String> optional() {
            return in.get() != 0 ? Optional.of(string()) : Optional.empty();
        }

        private Path path() {
            return Path.of(string());
        }

        private String string() {
            int index = in.getInt();
            if (index < 0 || index >= strings.length) {
                throw new IllegalArgumentException("Corrupt string reference " + index);
            }
            return strings[index];
        }

        /** A length or element count; never more than the bytes left, so a bad file cannot force a huge allocation. */
        static int count(ByteBuffer in) {
            int count = in.getInt();
            if (count < 0 || count > in.remaining()) {
                throw new IllegalArgumentException("Corrupt count " + count);
            }
            return count;
        }
    }
}
