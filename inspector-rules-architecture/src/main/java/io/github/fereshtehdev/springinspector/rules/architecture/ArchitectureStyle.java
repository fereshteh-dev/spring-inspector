package io.github.fereshtehdev.springinspector.rules.architecture;

/** An architecture style a project can follow; real projects often combine several. */
public enum ArchitectureStyle {
    LAYERED("Layered", "Code is organised by technical layer: controllers, services, repositories, domain."),
    PACKAGE_BY_FEATURE("Package by feature", "Code is organised by business feature, each feature in its own package."),
    HEXAGONAL(
            "Hexagonal (ports and adapters)",
            "A framework-free domain talks to the outside world through ports, implemented by adapters."),
    CLEAN(
            "Clean / onion architecture",
            "Use cases and entities in the centre, with infrastructure and presentation around them."),
    SPRING_MODULITH("Spring Modulith", "Application modules with explicit boundaries, verified by Spring Modulith."),
    CQRS("CQRS", "Commands that change state are separated from queries that read it."),
    EVENT_DRIVEN("Event-driven", "Parts of the application communicate through events or message brokers."),
    MULTI_MODULE("Multi-module build", "The build is split into several Maven or Gradle modules."),
    MULTIPLE_APPLICATIONS(
            "Several applications", "More than one Spring Boot application lives in this repository (services).");

    private final String title;
    private final String description;

    ArchitectureStyle(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
