package io.github.fereshtehdev.springinspector.rules.security;

import io.github.fereshtehdev.springinspector.core.domain.Reference;
import java.net.URI;

/** Links that several security findings point to. */
final class SecurityReferences {

    private SecurityReferences() {}

    static Reference cwe(int number, String title) {
        return new Reference(
                "CWE-" + number + ": " + title,
                URI.create("https://cwe.mitre.org/data/definitions/" + number + ".html"));
    }

    static Reference owaspCheatSheet(String title, String page) {
        return new Reference(
                "OWASP " + title + " Cheat Sheet",
                URI.create("https://cheatsheetseries.owasp.org/cheatsheets/" + page + ".html"));
    }

    static Reference springDocs(String title, String path) {
        return new Reference(title, URI.create("https://docs.spring.io/" + path));
    }
}
