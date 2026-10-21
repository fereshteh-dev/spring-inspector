package io.github.fereshtehdev.springinspector.cli.render;

import io.github.fereshtehdev.springinspector.core.domain.Category;
import java.util.Locale;
import java.util.Optional;

/** Category names as people type and read them: {@code spring-boot} and "Spring Boot". */
public final class Categories {

    private Categories() {}

    public static String title(Category category) {
        return switch (category) {
            case SECURITY -> "Security";
            case SPRING_BOOT -> "Spring Boot";
            case SPRING_AI -> "Spring AI";
            case CLEAN_CODE -> "Clean code";
            case ARCHITECTURE -> "Architecture";
            case MIGRATION -> "Migration";
        };
    }

    public static String option(Category category) {
        return category.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Accepts {@code spring-boot}, {@code spring_boot} and {@code SPRING_BOOT}. */
    public static Optional<Category> parse(String text) {
        String normalized = text.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Category category : Category.values()) {
            if (category.name().equals(normalized)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
