package io.github.fereshtehdev.springinspector.core.domain.change;

import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import java.util.List;

public sealed interface ChangedLines {

    static ChangedLines entireFile() {
        return EntireFile.INSTANCE;
    }

    static ChangedLines hunks(List<Hunk> hunks) {
        return new Hunks(hunks);
    }

    default boolean touches(LineRange lines) {
        return switch (this) {
            case EntireFile entireFile -> true;
            case Hunks(List<Hunk> hunks) ->
                hunks.stream().anyMatch(hunk -> hunk.changedLines().overlaps(lines));
        };
    }

    enum EntireFile implements ChangedLines {
        INSTANCE
    }

    record Hunks(List<Hunk> hunks) implements ChangedLines {

        public Hunks {
            if (hunks.isEmpty()) {
                throw new IllegalArgumentException("A changed file has at least one hunk");
            }
            hunks = List.copyOf(hunks);
        }
    }
}
