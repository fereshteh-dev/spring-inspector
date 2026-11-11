package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Listener;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.ScheduledJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Methods that run without an HTTP request: on a message, on an application event or on a timer. */
final class Triggers {

    private record Source(String kind, List<String> members) {}

    private static final Map<String, Source> MESSAGES = Map.of(
            "KafkaListener", new Source("Kafka topic", List.of("topics", "topicPattern", "value")),
            "RabbitListener", new Source("RabbitMQ queue", List.of("queues", "value")),
            "JmsListener", new Source("JMS destination", List.of("destination")),
            "SqsListener", new Source("SQS queue", List.of("value", "queueNames")),
            "PulsarListener", new Source("Pulsar topic", List.of("topics", "topicPattern")));
    private static final List<String> EVENTS =
            List.of("EventListener", "TransactionalEventListener", "ApplicationModuleListener");

    private Triggers() {}

    static List<Listener> listeners(TypeDeclaration<?> type) {
        List<Listener> listeners = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            String handler = type.getNameAsString() + "." + method.getNameAsString();
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String name = annotation.getName().getIdentifier();
                Source source = MESSAGES.get(name);
                if (source != null) {
                    List<String> names =
                            Annotations.values(annotation, source.members().toArray(String[]::new));
                    listeners.add(new Listener(source.kind(), String.join(", ", names), handler));
                } else if (EVENTS.contains(name)) {
                    listeners.add(new Listener("Application event", eventType(annotation, method), handler));
                }
            }
        }
        return listeners;
    }

    static List<ScheduledJob> jobs(TypeDeclaration<?> type) {
        List<ScheduledJob> jobs = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            Annotations.find(method, "Scheduled")
                    .ifPresent(scheduled -> jobs.add(new ScheduledJob(
                            schedule(scheduled), type.getNameAsString() + "." + method.getNameAsString())));
        }
        return jobs;
    }

    private static String eventType(AnnotationExpr annotation, MethodDeclaration method) {
        List<String> declared = Annotations.values(annotation, "value", "classes");
        if (!declared.isEmpty()) {
            return String.join(", ", declared);
        }
        return method.getParameters().stream()
                .findFirst()
                .map(parameter -> parameter.getType().asString())
                .orElse("any event");
    }

    private static String schedule(AnnotationExpr scheduled) {
        Optional<String> cron = Annotations.value(scheduled, "cron");
        if (cron.isPresent()) {
            return "cron " + cron.get();
        }
        Optional<String> rate = Annotations.value(scheduled, "fixedRate", "fixedRateString");
        if (rate.isPresent()) {
            return "every " + rate.get() + unit(scheduled);
        }
        return Annotations.value(scheduled, "fixedDelay", "fixedDelayString")
                .map(delay -> delay + unit(scheduled) + " after the previous run")
                .orElse("see the annotation");
    }

    private static String unit(AnnotationExpr scheduled) {
        return Annotations.value(scheduled, "timeUnit")
                .map(unit -> " " + unit.replace("{", "").replace("}", "").toLowerCase(java.util.Locale.ROOT))
                .orElse(" ms");
    }
}
