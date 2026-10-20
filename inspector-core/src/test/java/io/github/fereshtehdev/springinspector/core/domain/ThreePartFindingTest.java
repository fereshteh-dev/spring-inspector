package io.github.fereshtehdev.springinspector.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThreePartFindingTest {

    private static final Reference CWE_89 =
            new Reference("CWE-89", URI.create("https://cwe.mitre.org/data/definitions/89.html"));

    @Test
    void requiresSayingWhatIsWrong() {
        assertThatThrownBy(() -> new WhatsWrong(" ", "code();")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAnEmptySnippet() {
        assertThat(new WhatsWrong("Unsafe query", "").snippet()).isEmpty();
    }

    @Test
    void requiresSayingWhyItMatters() {
        assertThatThrownBy(() -> new WhyItMatters("", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsReferencesImmutable() {
        List<Reference> references = new ArrayList<>(List.of(CWE_89));

        WhyItMatters why = new WhyItMatters("SQL injection", references);
        references.clear();

        assertThat(why.references()).containsExactly(CWE_89);
        assertThatThrownBy(() -> why.references().add(CWE_89)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresAReferenceLabel() {
        assertThatThrownBy(() -> new Reference(" ", URI.create("https://example.org")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresSayingHowToFix() {
        assertThatThrownBy(() -> HowToFix.withoutPatch(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carriesAnOptionalPatch() {
        Patch patch = new Patch("--- a/App.java\n+++ b/App.java\n");

        assertThat(HowToFix.withPatch("Use a bind parameter", patch).patch()).contains(patch);
        assertThat(HowToFix.withoutPatch("Rename the class").patch()).isEmpty();
    }

    @Test
    void rejectsAnEmptyPatch() {
        assertThatThrownBy(() -> new Patch("\n")).isInstanceOf(IllegalArgumentException.class);
    }
}
