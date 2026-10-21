package io.github.fereshtehdev.springinspector.rules.security;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Broken hash functions and ciphers: MD5 and SHA-1 digests, DES/RC4-class ciphers, and ECB mode. */
public final class WeakCryptographyCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("security.weak-cryptography");

    private static final String MESSAGE_DIGEST = "java.security.MessageDigest";
    private static final String CIPHER = "javax.crypto.Cipher";
    private static final String SPRING_DIGEST_UTILS = "org.springframework.util.DigestUtils";
    private static final String CODEC_DIGEST_UTILS = "org.apache.commons.codec.digest.DigestUtils";

    private static final Set<String> WEAK_DIGESTS = Set.of("MD2", "MD4", "MD5", "SHA", "SHA1", "SHA-1");
    private static final Set<String> WEAK_CIPHERS =
            Set.of("DES", "DESEDE", "TRIPLEDES", "RC2", "RC4", "ARCFOUR", "BLOWFISH");
    private static final Set<String> SPRING_WEAK_METHODS =
            Set.of("md5Digest", "md5DigestAsHex", "appendMd5DigestAsHex");
    private static final Map<String, String> CODEC_STRONG_REPLACEMENTS = Map.of(
            "md5", "sha256",
            "md5Hex", "sha256Hex",
            "md2", "sha256",
            "md2Hex", "sha256Hex",
            "sha", "sha256",
            "shaHex", "sha256Hex",
            "sha1", "sha256",
            "sha1Hex", "sha256Hex");

    private static final List<Reference> REFERENCES = List.of(
            new Reference(
                    "CWE-327: Broken or risky cryptographic algorithm",
                    URI.create("https://cwe.mitre.org/data/definitions/327.html")),
            new Reference(
                    "OWASP Password Storage Cheat Sheet",
                    URI.create("https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html")));

    private static final WhyItMatters WEAK_HASH = new WhyItMatters(
            "MD5 and SHA-1 have practical collision attacks, so they cannot prove integrity or authenticity, and they"
                    + " are fast enough to brute-force passwords on ordinary hardware.",
            REFERENCES);

    private static final WhyItMatters WEAK_CIPHER = new WhyItMatters(
            "DES, 3DES, RC2, RC4 and Blowfish have short keys or blocks or known biases. ECB mode, the default for"
                    + " a bare \"AES\", encrypts equal blocks to equal ciphertext and leaks the data's structure.",
            REFERENCES);

    private static final String PASSWORD_ADVICE = " If this hashes passwords, use Spring Security's"
            + " PasswordEncoder (Argon2 or BCrypt) instead of any plain digest.";

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SECURITY;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<Finding> findings = new ArrayList<>();
        for (MethodCallExpr call : file.unit().findAll(MethodCallExpr.class)) {
            weakDigest(file, call)
                    .or(() -> weakCipher(file, call))
                    .or(() -> weakDigestUtility(file, call))
                    .ifPresent(findings::add);
        }
        return findings;
    }

    private Optional<Finding> weakDigest(JavaFile file, MethodCallExpr call) {
        Optional<StringLiteralExpr> algorithm = literalArgumentOf(file, call, MESSAGE_DIGEST, "getInstance");
        if (algorithm.isEmpty()
                || !WEAK_DIGESTS.contains(algorithm.get().getValue().toUpperCase(Locale.ROOT))) {
            return Optional.empty();
        }
        HowToFix fix = HowToFix.withPatch(
                "Use SHA-256 or stronger for integrity checks." + PASSWORD_ADVICE,
                file.patch(List.of(file.replace(algorithm.get(), "\"SHA-256\""))));
        return Optional.of(weak(file, call, algorithm.get().getValue() + " is a broken hash function", WEAK_HASH, fix));
    }

    private Optional<Finding> weakCipher(JavaFile file, MethodCallExpr call) {
        Optional<StringLiteralExpr> transformation = literalArgumentOf(file, call, CIPHER, "getInstance");
        if (transformation.isEmpty()) {
            return Optional.empty();
        }
        String[] parts =
                transformation.get().getValue().toUpperCase(Locale.ROOT).split("/", -1);
        boolean weakAlgorithm = WEAK_CIPHERS.contains(parts[0]);
        boolean ecb = (parts.length > 1 && parts[1].equals("ECB")) || (parts.length == 1 && parts[0].equals("AES"));
        if (!weakAlgorithm && !ecb) {
            return Optional.empty();
        }
        String problem = weakAlgorithm
                ? parts[0] + " is a broken cipher"
                : "\"" + transformation.get().getValue() + "\" encrypts in ECB mode";
        HowToFix fix = HowToFix.withoutPatch("Use \"AES/GCM/NoPadding\" with a fresh random 12-byte IV for every"
                + " message, and store the IV next to the ciphertext.");
        return Optional.of(weak(file, call, problem, WEAK_CIPHER, fix));
    }

    private Optional<Finding> weakDigestUtility(JavaFile file, MethodCallExpr call) {
        String method = call.getNameAsString();
        if (isStaticCallOn(file, call, SPRING_DIGEST_UTILS) && SPRING_WEAK_METHODS.contains(method)) {
            HowToFix fix = HowToFix.withoutPatch(
                    "Use MessageDigest.getInstance(\"SHA-256\"), or HexFormat to" + " print it." + PASSWORD_ADVICE);
            return Optional.of(weak(file, call, "Spring's DigestUtils." + method + " uses MD5", WEAK_HASH, fix));
        }
        String replacement = CODEC_STRONG_REPLACEMENTS.get(method);
        if (replacement != null && isStaticCallOn(file, call, CODEC_DIGEST_UTILS)) {
            HowToFix fix = HowToFix.withPatch(
                    "Use DigestUtils." + replacement + " instead." + PASSWORD_ADVICE,
                    file.patch(List.of(file.replace(call.getName(), replacement))));
            return Optional.of(
                    weak(file, call, "DigestUtils." + method + " uses a broken hash function", WEAK_HASH, fix));
        }
        return Optional.empty();
    }

    private Finding weak(JavaFile file, MethodCallExpr call, String problem, WhyItMatters why, HowToFix fix) {
        return finding(file, call, Severity.MAJOR, Confidence.HIGH, problem, why, fix);
    }

    private static Optional<StringLiteralExpr> literalArgumentOf(
            JavaFile file, MethodCallExpr call, String type, String method) {
        if (!call.getNameAsString().equals(method)
                || call.getArguments().isEmpty()
                || !isStaticCallOn(file, call, type)) {
            return Optional.empty();
        }
        Expression argument = call.getArgument(0);
        return argument instanceof StringLiteralExpr literal ? Optional.of(literal) : Optional.empty();
    }

    private static boolean isStaticCallOn(JavaFile file, MethodCallExpr call, String type) {
        return call.getScope()
                .map(scope -> file.refersTo(scope.toString(), type))
                .orElse(false);
    }
}
