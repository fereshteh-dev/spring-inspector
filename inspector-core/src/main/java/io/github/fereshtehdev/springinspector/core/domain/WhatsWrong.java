package io.github.fereshtehdev.springinspector.core.domain;

public record WhatsWrong(String description, String snippet) {

    public WhatsWrong {
        if (description.isBlank()) {
            throw new IllegalArgumentException("A finding must say what is wrong");
        }
    }
}
