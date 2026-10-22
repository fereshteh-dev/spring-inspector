package io.github.fereshtehdev.springinspector.core.domain.release;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public facts about the release lines of Java, Spring Boot and Spring AI: when each line came out, when its free
 * support ends, and its newest release. Downloaded by {@code update}, so rules about support and upgrades stay
 * correct without a new version of Spring Inspector.
 *
 * @param products release lines by product, such as {@link #SPRING_BOOT}
 * @param fetchedAt when the facts were downloaded
 */
public record ReleaseCalendar(Map<String, List<ReleaseLine>> products, Instant fetchedAt) {

    public static final String JAVA = "java";
    public static final String SPRING_BOOT = "spring-boot";
    public static final String SPRING_AI = "spring-ai";

    public ReleaseCalendar {
        products = Map.copyOf(products);
    }

    /** The release line, such as {@code 3.5} or {@code 25}, of a product. */
    public Optional<ReleaseLine> line(String product, String line) {
        return products.getOrDefault(product, List.of()).stream()
                .filter(candidate -> candidate.line().equals(line))
                .findFirst();
    }

    /**
     * One release line.
     *
     * @param line the line's name, such as {@code 3.5} for Spring Boot or {@code 25} for Java
     * @param released when its first release came out, if known
     * @param supportEnds when free (open-source) updates for it end, if known
     * @param latest its newest release, such as {@code 3.5.16}, if known
     */
    public record ReleaseLine(
            String line, Optional<LocalDate> released, Optional<LocalDate> supportEnds, Optional<String> latest) {}
}
