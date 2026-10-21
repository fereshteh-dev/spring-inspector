package io.github.fereshtehdev.springinspector.javamodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguageLevelsTest {

    private static LanguageLevel forJava(String version) {
        return LanguageLevels.forJava(Optional.of(Version.parse(version)));
    }

    @Test
    void usesTheProjectsJavaRelease() {
        assertThat(forJava("21")).isEqualTo(LanguageLevel.JAVA_21);
        assertThat(forJava("17.0.2")).isEqualTo(LanguageLevel.JAVA_17);
    }

    @Test
    void understandsTheOldOnePointVersionScheme() {
        assertThat(forJava("1.8")).isEqualTo(LanguageLevel.JAVA_8);
    }

    @Test
    void releasesOlderThanJava8AreParsedAsJava8() {
        assertThat(forJava("1.6")).isEqualTo(LanguageLevel.JAVA_8);
    }

    @Test
    void releasesNewerThanTheParserKnowsUseTheNewestItSupports() {
        assertThat(forJava("99")).isEqualTo(LanguageLevel.JAVA_26);
    }

    @Test
    void anUnknownReleaseIsParsedAsJava25() {
        assertThat(LanguageLevels.forJava(Optional.empty())).isEqualTo(LanguageLevel.JAVA_25);
    }
}
