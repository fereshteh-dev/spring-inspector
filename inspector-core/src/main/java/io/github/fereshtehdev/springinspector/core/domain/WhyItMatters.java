package io.github.fereshtehdev.springinspector.core.domain;

import java.util.List;

public record WhyItMatters(String explanation, List<Reference> references) {

    public WhyItMatters {
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("A finding must say why it matters");
        }
        references = List.copyOf(references);
    }
}
