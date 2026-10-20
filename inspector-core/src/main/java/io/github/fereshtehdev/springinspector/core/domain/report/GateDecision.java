package io.github.fereshtehdev.springinspector.core.domain.report;

import java.util.List;

public sealed interface GateDecision {

    static GateDecision pass() {
        return Pass.INSTANCE;
    }

    default boolean blocks() {
        return switch (this) {
            case Pass pass -> false;
            case Block block -> true;
        };
    }

    enum Pass implements GateDecision {
        INSTANCE
    }

    record Block(List<String> reasons) implements GateDecision {

        public Block {
            if (reasons.isEmpty()) {
                throw new IllegalArgumentException("A blocking gate decision needs a reason");
            }
            reasons = List.copyOf(reasons);
        }
    }
}
