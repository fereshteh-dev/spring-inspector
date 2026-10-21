package io.github.fereshtehdev.springinspector.core.domain.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeScopeTest {

    @Test
    void requiresABaseRefToCompareAgainst() {
        assertThatThrownBy(() -> ChangeScope.againstBase(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheBaseRef() {
        assertThat(ChangeScope.againstBase("origin/main")).isEqualTo(new ChangeScope.AgainstBase("origin/main"));
    }

    @Test
    void offersStagedAndAllScopes() {
        assertThat(ChangeScope.staged()).isSameAs(ChangeScope.Staged.INSTANCE);
        assertThat(ChangeScope.all()).isSameAs(ChangeScope.All.INSTANCE);
    }

    @Test
    void namesWholeFilesInsideTheProject() {
        assertThat(ChangeScope.files(List.of(Path.of("src/A.java"))))
                .isEqualTo(new ChangeScope.Files(List.of(Path.of("src/A.java"))));
        assertThatThrownBy(() -> ChangeScope.files(List.of())).hasMessageContaining("at least one file");
        assertThatThrownBy(() -> ChangeScope.files(List.of(Path.of("../Other.java"))))
                .hasMessageContaining("escapes the project root");
        assertThatThrownBy(() -> ChangeScope.files(List.of(Path.of("src/A.java").toAbsolutePath())))
                .hasMessageContaining("relative");
    }
}
