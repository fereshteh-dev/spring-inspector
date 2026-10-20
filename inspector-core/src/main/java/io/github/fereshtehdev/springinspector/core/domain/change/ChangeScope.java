package io.github.fereshtehdev.springinspector.core.domain.change;

public sealed interface ChangeScope {

    static ChangeScope staged() {
        return Staged.INSTANCE;
    }

    static ChangeScope againstBase(String baseRef) {
        return new AgainstBase(baseRef);
    }

    static ChangeScope all() {
        return All.INSTANCE;
    }

    enum Staged implements ChangeScope {
        INSTANCE
    }

    record AgainstBase(String baseRef) implements ChangeScope {

        public AgainstBase {
            if (baseRef.isBlank()) {
                throw new IllegalArgumentException("A base ref is required");
            }
        }
    }

    enum All implements ChangeScope {
        INSTANCE
    }
}
