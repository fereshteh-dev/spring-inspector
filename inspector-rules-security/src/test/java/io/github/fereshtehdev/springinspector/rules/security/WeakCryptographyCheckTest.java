package io.github.fereshtehdev.springinspector.rules.security;

import static io.github.fereshtehdev.springinspector.testkit.CheckTester.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.testkit.CheckTester;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeakCryptographyCheckTest {

    private static final String FIXTURES = "fixtures/security.weak-cryptography/";

    private final WeakCryptographyCheck check = new WeakCryptographyCheck();

    private CheckTester bad() {
        return CheckTester.of(check).source(resource(FIXTURES + "bad/Hashing.java"));
    }

    private static Finding onLine(List<Finding> findings, int line) {
        return findings.stream()
                .filter(finding -> finding.location().lines().start() == line)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void identifiesItselfAsASecurityRule() {
        assertThat(check.id()).isEqualTo(WeakCryptographyCheck.ID);
        assertThat(check.category()).isEqualTo(Category.SECURITY);
    }

    @Test
    void reportsBrokenDigestsCiphersAndDigestUtilities() {
        List<Finding> findings = bad().findings();

        assertThat(findings)
                .extracting(
                        finding -> finding.location().lines().start(),
                        finding -> finding.whatsWrong().description())
                .containsExactly(
                        tuple(11, "MD5 is a broken hash function"),
                        tuple(15, "sha-1 is a broken hash function"),
                        tuple(19, "DES is a broken cipher"),
                        tuple(23, "\"AES\" encrypts in ECB mode"),
                        tuple(27, "DigestUtils.md5Hex uses a broken hash function"),
                        tuple(31, "Spring's DigestUtils.md5DigestAsHex uses MD5"));
    }

    @Test
    void replacesABrokenDigestAlgorithmWithSha256() {
        CheckTester bad = bad();
        Finding md5 = onLine(bad.findings(), 11);

        assertThat(bad.fixed(md5)).contains("MessageDigest.getInstance(\"SHA-256\")");
        assertThat(bad.afterFix(md5).findings())
                .extracting(finding -> finding.location().lines().start())
                .doesNotContain(11);
    }

    @Test
    void replacesACommonsCodecDigestWithItsSha256Variant() {
        CheckTester bad = bad();
        Finding md5Hex = onLine(bad.findings(), 27);

        assertThat(bad.fixed(md5Hex)).contains("return DigestUtils.sha256Hex(text);");
    }

    @Test
    void cipherProblemsNeedAHumanFix() {
        List<Finding> findings = bad().findings();

        assertThat(onLine(findings, 19).howToFix().patch()).isEmpty();
        assertThat(onLine(findings, 19).howToFix().explanation()).contains("AES/GCM/NoPadding");
    }

    @Test
    void acceptsStrongAlgorithmsAndUnrelatedMethodsWithTheSameName() {
        assertThat(CheckTester.of(check)
                        .source(resource(FIXTURES + "good/Hashing.java"))
                        .findings())
                .isEmpty();
    }
}
