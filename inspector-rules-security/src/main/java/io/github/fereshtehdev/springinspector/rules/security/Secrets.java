package io.github.fereshtehdev.springinspector.rules.security;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a secret looks like: well-known token formats, names that hold secrets, and values that could be one. Values
 * that read like words or identifiers ({@code X-Auth-Token}) are not taken for secrets, which keeps headers, labels
 * and property names out of the report.
 */
final class Secrets {

    private static final Map<String, Pattern> KNOWN_TOKENS = knownTokens();

    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            ".*(password|passwd|passphrase|secret|token|apikey|credential|privatekey|accesskey|clientsecret).*");

    private static final Pattern METADATA_NAME = Pattern.compile(
            ".*(count|length|size|expir|type|url|uri|endpoint|header|name|limit|usage|ttl|policy|encoder|field|param).*");

    private static final Pattern IDENTIFIER_LIKE = Pattern.compile("[A-Za-z]+(?:[-_.][A-Za-z]+)*");

    private static final Set<String> WEAK_DEFAULTS =
            Set.of("changeit", "changeme", "password", "admin", "secret", "123456", "12345678", "qwerty", "letmein");

    private static final int MIN_LENGTH = 8;

    private Secrets() {}

    private static Map<String, Pattern> knownTokens() {
        Map<String, Pattern> tokens = new LinkedHashMap<>();
        tokens.put("private key", Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"));
        tokens.put("AWS access key", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"));
        tokens.put("GitHub token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b"));
        tokens.put("Slack token", Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"));
        tokens.put("Google API key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"));
        tokens.put("Stripe secret key", Pattern.compile("\\b[rs]k_live_[0-9A-Za-z]{20,}\\b"));
        tokens.put("Anthropic API key", Pattern.compile("\\bsk-ant-[A-Za-z0-9_-]{20,}"));
        tokens.put("OpenAI API key", Pattern.compile("\\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}"));
        tokens.put(
                "JSON Web Token",
                Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"));
        return tokens;
    }

    /** The kind of a well-known token the text contains, such as "AWS access key". */
    static Optional<String> knownTokenKind(String text) {
        return KNOWN_TOKENS.entrySet().stream()
                .filter(token -> token.getValue().matcher(text).find())
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * {@code dbPassword}, {@code API_KEY}, {@code client-secret}: names under which secrets are kept. Names about a
     * secret rather than holding one, such as {@code tokenCount} or {@code passwordResetUrl}, do not count.
     */
    static boolean isSensitiveName(String name) {
        String normalized =
                name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(".", "");
        return SENSITIVE_NAME.matcher(normalized).matches()
                && !METADATA_NAME.matcher(normalized).matches();
    }

    /** Whether a literal kept under a sensitive name could be the secret itself, rather than a label or a key. */
    static boolean couldBeSecret(String value) {
        String trimmed = value.strip();
        if (WEAK_DEFAULTS.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return trimmed.length() >= MIN_LENGTH
                && trimmed.chars().noneMatch(Character::isWhitespace)
                && !trimmed.contains("${")
                && !trimmed.contains("://")
                && !IDENTIFIER_LIKE.matcher(trimmed).matches();
    }

    /** The text with the secret shortened to its first characters, so reports never repeat it. */
    static String mask(String text, String secret) {
        if (secret.isEmpty()) {
            return text;
        }
        String visible = secret.substring(0, Math.min(4, secret.length() / 4));
        return text.replace(secret, visible + "****");
    }
}
