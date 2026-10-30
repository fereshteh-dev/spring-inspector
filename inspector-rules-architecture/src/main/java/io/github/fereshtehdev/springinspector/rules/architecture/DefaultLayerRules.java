package io.github.fereshtehdev.springinspector.rules.architecture;

import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.architecture.JavaUnit;
import java.util.Optional;

/** Which dependencies between layers break the built-in layer rules, and what to say about them. */
final class DefaultLayerRules {

    private DefaultLayerRules() {}

    static Optional<Violation> violation(Layer from, Layer to, JavaUnit unit, JavaUnit target) {
        String user = unit.typeName();
        String used = target.typeName();
        return switch (from) {
            case WEB ->
                to == Layer.REPOSITORY
                        ? Optional.of(new Violation(
                                Severity.MINOR,
                                user + " uses the repository " + used + " directly",
                                "Controllers that call repositories skip the service layer, where business rules,"
                                        + " validation and transactions live. The same logic then gets copied"
                                        + " into several controllers, and it cannot be tested without HTTP.",
                                "Call a service method instead, and let the service use " + used + "."))
                        : Optional.empty();
            case SERVICE ->
                to == Layer.WEB
                        ? Optional.of(new Violation(
                                Severity.MAJOR,
                                user + " depends on the web class " + used,
                                "A service that knows about controllers or web request types is tied to HTTP: it"
                                        + " cannot be used from a scheduled job, a message listener or a test"
                                        + " without the web layer.",
                                "Move " + used + " into the service layer if it is a plain data type, or map"
                                        + " between web types and service types in the controller."))
                        : Optional.empty();
            case REPOSITORY ->
                to == Layer.WEB || to == Layer.SERVICE
                        ? Optional.of(new Violation(
                                Severity.MAJOR,
                                user + " depends on the " + to.label() + " class " + used,
                                "Repositories sit below services and controllers. Depending upwards creates a"
                                        + " cycle between layers, so neither can change or be tested alone.",
                                "Keep " + user + " to data access; move what it needs from " + used
                                        + " down into the domain, or let the service pass it in."))
                        : Optional.empty();
            case DOMAIN ->
                to == Layer.WEB || to == Layer.SERVICE || to == Layer.REPOSITORY
                        ? Optional.of(new Violation(
                                Severity.MAJOR,
                                "The domain class " + user + " depends on the " + to.label() + " class " + used,
                                "Domain classes are the heart of the application and should depend on nothing"
                                        + " above them. When they do, every change in the outer layers can break"
                                        + " the business model.",
                                "Turn the dependency around: let the " + to.label() + " layer use " + user
                                        + ", or define an interface in the domain that " + used + " implements."))
                        : Optional.empty();
            case CONFIG, OTHER -> Optional.empty();
        };
    }

    record Violation(Severity severity, String description, String why, String howToFix) {}
}
