package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import java.util.List;
import java.util.Set;

/**
 * The layer of a class in a Spring application: from its stereotype annotation first, then its name, then its
 * package, so both {@code @Service class Billing} and {@code orders.service.Billing} are services.
 */
enum Layer {
    WEB("web"),
    SERVICE("service"),
    REPOSITORY("repository"),
    DOMAIN("domain"),
    CONFIG("configuration"),
    OTHER("other");

    private static final Set<String> WEB_STEREOTYPES =
            Set.of("RestController", "Controller", "ControllerAdvice", "RestControllerAdvice");
    private static final Set<String> DOMAIN_STEREOTYPES =
            Set.of("Entity", "Embeddable", "MappedSuperclass", "Document", "Table");

    private final String label;

    Layer(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    static Layer of(JavaUnit unit) {
        Set<String> stereotypes = unit.stereotypes();
        if (stereotypes.stream().anyMatch(WEB_STEREOTYPES::contains)) {
            return WEB;
        }
        if (stereotypes.contains("Service")) {
            return SERVICE;
        }
        if (stereotypes.contains("Repository")) {
            return REPOSITORY;
        }
        if (stereotypes.stream().anyMatch(DOMAIN_STEREOTYPES::contains)) {
            return DOMAIN;
        }
        if (stereotypes.contains("Configuration") || stereotypes.contains("SpringBootApplication")) {
            return CONFIG;
        }
        String type = unit.typeName();
        if (type.endsWith("Controller")) {
            return WEB;
        }
        if (type.endsWith("Repository") || type.endsWith("Dao")) {
            return REPOSITORY;
        }
        if (type.endsWith("Service") || type.endsWith("ServiceImpl")) {
            return SERVICE;
        }
        return ofPackage(unit.packageName());
    }

    /** The innermost package segment that names a layer, such as {@code web} in {@code com.acme.orders.web}. */
    static Layer ofPackage(String packageName) {
        List<String> segments = Packages.segments(packageName);
        for (String segment : segments.reversed()) {
            switch (segment) {
                case "controller", "controllers", "web", "rest" -> {
                    return WEB;
                }
                case "service", "services" -> {
                    return SERVICE;
                }
                case "repository", "repositories", "repo", "dao", "persistence" -> {
                    return REPOSITORY;
                }
                case "domain", "model", "entity", "entities" -> {
                    return DOMAIN;
                }
                case "config", "configuration" -> {
                    return CONFIG;
                }
                default -> {}
            }
        }
        return OTHER;
    }
}
