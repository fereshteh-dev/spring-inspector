package io.github.fereshtehdev.springinspector.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LauncherTest {

    @Test
    void quotesPathsForShWithForwardSlashes() {
        String command = Launcher.command(
                Path.of("C:\\Program Files\\Java\\bin\\java"),
                Path.of("D:\\it's\\si.jar"),
                Path.of("C:\\Users\\me\\.spring-inspector\\spring-inspector.jsa"));

        assertThat(command)
                .isEqualTo("'C:/Program Files/Java/bin/java'"
                        + " -XX:SharedArchiveFile='C:/Users/me/.spring-inspector/spring-inspector.jsa'"
                        + " -XX:+AutoCreateSharedArchive -Xlog:cds*=off -Xlog:aot*=off -jar 'D:/it'\\''s/si.jar'");
    }

    @Test
    void hasNoCommandWhenRunningFromClassDirectories() {
        assertThat(Launcher.current(new InspectorHome(Path.of("unused")))).isEmpty();
    }

    @Test
    void keepsItsFilesInTheConfiguredHome() {
        Path userHome = Path.of("users", "me");

        assertThat(InspectorHome.from(Map.of(), userHome).cacheKey())
                .isEqualTo(userHome.resolve(".spring-inspector").resolve("cache.key"));
        assertThat(InspectorHome.from(Map.of(InspectorHome.VARIABLE, "elsewhere"), userHome)
                        .directory())
                .isEqualTo(Path.of("elsewhere"));
        assertThat(InspectorHome.from(Map.of(InspectorHome.VARIABLE, " "), userHome)
                        .directory())
                .isEqualTo(userHome.resolve(".spring-inspector"));
    }

    @Test
    void discoversEveryRuleModulesChecks() {
        assertThat(SpringInspector.checks())
                .extracting(check -> check.id().value())
                .contains(
                        "security.sql-concatenation",
                        "security.weak-cryptography",
                        "spring.field-injection",
                        "spring.jackson2-in-boot4",
                        "springai.system-prompt-concatenation",
                        "springai.shared-conversation-id",
                        "cleancode.swallowed-exception",
                        "cleancode.long-method");
    }
}
