package io.github.fereshtehdev.springinspector.core.domain.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
