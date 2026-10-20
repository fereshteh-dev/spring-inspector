package io.github.fereshtehdev.springinspector.core.domain;

import java.net.URI;

public record Reference(String label, URI uri) {

    public Reference {
        if (label.isBlank()) {
            throw new IllegalArgumentException("A reference needs a label");
        }
    }
}
