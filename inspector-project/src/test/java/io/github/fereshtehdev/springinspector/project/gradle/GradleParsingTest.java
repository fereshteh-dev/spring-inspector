package io.github.fereshtehdev.springinspector.project.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fereshtehdev.springinspector.project.gradle.VersionCatalog.Library;
import io.github.fereshtehdev.springinspector.project.gradle.VersionCatalog.Plugin;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GradleParsingTest {

    @Test
    void dropsLineAndBlockCommentsButKeepsNewlines() {
        String script = "a // gone\n/* also\ngone */b";

        assertThat(ScriptText.withoutComments(script)).isEqualTo("a \n\nb");
    }

    @Test
    void keepsCommentMarkersInsideStrings() {
        String script = "url 'https://repo.example.org' // mirror\nname = \"/* not a comment */\"";

        assertThat(ScriptText.withoutComments(script))
                .isEqualTo("url 'https://repo.example.org' \nname = \"/* not a comment */\"");
    }

    @Test
    void handlesTripleQuotedStringsAndEscapedQuotes() {
        String script = "def s = \"\"\"// keep\"\"\"\ndef t = 'it\\'s' // drop";

        assertThat(ScriptText.withoutComments(script)).isEqualTo("def s = \"\"\"// keep\"\"\"\ndef t = 'it\\'s' ");
    }

    @Test
    void survivesUnterminatedCommentsAndStrings() {
        assertThat(ScriptText.withoutComments("a /* open")).isEqualTo("a ");
        assertThat(ScriptText.withoutComments("a 'open")).isEqualTo("a 'open");
        assertThat(ScriptText.withoutComments("a // end")).isEqualTo("a ");
    }

    @Test
    void readsLibrariesAndPluginsInEveryNotation() {
        VersionCatalog catalog = VersionCatalog.parse("""
                [versions]
                boot = "4.1.1"

                [libraries]
                ai-bom = { group = "org.springframework.ai", name = "spring-ai-bom", version = "2.0.1" }
                ai-ref = { module = "org.springframework.ai:spring-ai-model", version.ref = "missing" }
                assertj = "org.assertj:assertj-core:3.27.7"
                junit = "org.junit.jupiter:junit-jupiter"
                broken = "not-a-coordinate"
                nameless = { version = "1" }

                [plugins]
                boot = { id = "org.springframework.boot", version.ref = "boot" }
                versions = "com.github.ben-manes.versions:0.52.0"
                bare = "com.example.plugin"

                [bundles]
                test = ["assertj", "junit"]
                """);

        assertThat(catalog.libraries())
                .containsExactly(
                        new Library("org.springframework.ai", "spring-ai-bom", Optional.of("2.0.1")),
                        new Library("org.springframework.ai", "spring-ai-model", Optional.empty()),
                        new Library("org.assertj", "assertj-core", Optional.of("3.27.7")),
                        new Library("org.junit.jupiter", "junit-jupiter", Optional.empty()));
        assertThat(catalog.plugins())
                .containsExactly(
                        new Plugin("org.springframework.boot", Optional.of("4.1.1")),
                        new Plugin("com.github.ben-manes.versions", Optional.of("0.52.0")),
                        new Plugin("com.example.plugin", Optional.empty()));
    }

    @Test
    void ignoresCatalogCommentsButNotHashesInsideStrings() {
        VersionCatalog catalog = VersionCatalog.parse("""
                [libraries] # comment
                odd = "com.example:lib#name:1.0" # trailing
                """);

        assertThat(catalog.libraries()).containsExactly(new Library("com.example", "lib#name", Optional.of("1.0")));
    }
}
