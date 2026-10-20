package io.github.fereshtehdev.springinspector.project.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PomReaderTest {

    @Test
    void rejectsDocumentsThatAreNotPoms() {
        assertThatThrownBy(() -> PomReader.parse("<settings/>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<settings>");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> PomReader.parse("<project><properties></project>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsOnlyDirectChildrenSoPluginDependenciesAreNotProjectDependencies() {
        Pom pom = PomReader.parse("""
                <project>
                  <build>
                    <plugins>
                      <plugin>
                        <artifactId>some-plugin</artifactId>
                        <dependencies>
                          <dependency><groupId>org.springframework.ai</groupId><artifactId>x</artifactId><version>9</version></dependency>
                        </dependencies>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        assertThat(pom.dependencies()).isEmpty();
        assertThat(pom.plugins()).singleElement().satisfies(plugin -> {
            assertThat(plugin.coordinates().groupId()).isEqualTo("org.apache.maven.plugins");
            assertThat(plugin.coordinates().version()).isEmpty();
        });
    }

    @Test
    void readsPluginsFromPluginManagementToo() {
        Pom pom = PomReader.parse("""
                <project>
                  <build>
                    <pluginManagement>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <configuration><source>21</source><target>21</target></configuration>
                        </plugin>
                      </plugins>
                    </pluginManagement>
                  </build>
                </project>
                """);

        assertThat(pom.plugins())
                .singleElement()
                .satisfies(plugin -> assertThat(plugin.config("target")).contains("21"));
    }
}
