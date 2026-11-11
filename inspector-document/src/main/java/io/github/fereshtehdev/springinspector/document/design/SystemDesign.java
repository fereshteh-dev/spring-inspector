package io.github.fereshtehdev.springinspector.document.design;

import java.util.List;
import java.util.Optional;

/**
 * What a project is made of and how its parts work together, read from the source without running it: where
 * requests come in, what is stored, what it talks to, how it is secured and how it uses AI models.
 *
 * @param applications classes annotated with {@code @SpringBootApplication}
 * @param flows for every entry point, the components it uses
 */
public record SystemDesign(
        List<String> applications,
        List<ComponentCount> components,
        List<Technology> technologies,
        List<Endpoint> endpoints,
        List<EntityModel> entities,
        List<Listener> listeners,
        List<ScheduledJob> jobs,
        List<Integration> integrations,
        SecurityDesign security,
        AiDesign ai,
        List<Flow> flows,
        RuntimeSettings settings) {

    public SystemDesign {
        applications = List.copyOf(applications);
        components = List.copyOf(components);
        technologies = List.copyOf(technologies);
        endpoints = List.copyOf(endpoints);
        entities = List.copyOf(entities);
        listeners = List.copyOf(listeners);
        jobs = List.copyOf(jobs);
        integrations = List.copyOf(integrations);
        flows = List.copyOf(flows);
    }

    /**
     * How many classes play a role.
     *
     * @param role such as {@code Controllers} or {@code Services}
     */
    public record ComponentCount(String role, int classes) {}

    /**
     * A framework or product the project uses.
     *
     * @param classes how many classes use it
     */
    public record Technology(String name, int classes) {}

    /**
     * A way into the application over HTTP, GraphQL or a messaging channel.
     *
     * @param method the HTTP method, or {@code QUERY}, {@code MUTATION}, {@code SUBSCRIPTION} or {@code MESSAGE}
     * @param handler the class and method that handle it
     * @param requestBody the type read from the request body, or empty
     * @param access the method security expression on the handler, or empty when only the filter chain applies
     */
    public record Endpoint(
            String method, String path, String handler, String requestBody, String response, String access) {}

    /**
     * A stored type.
     *
     * @param store the table or collection name when it is declared, or empty
     */
    public record EntityModel(String name, String store, String idField, int fields, List<Relation> relations) {

        public EntityModel {
            relations = List.copyOf(relations);
        }
    }

    /**
     * A link from one stored type to another.
     *
     * @param kind the mapping annotation, such as {@code OneToMany}
     */
    public record Relation(String kind, String field, String target) {}

    /**
     * A method that runs when a message or event arrives.
     *
     * @param kind such as {@code Kafka topic} or {@code Application event}
     * @param source the topic, queue or event type
     */
    public record Listener(String kind, String source, String handler) {}

    public record ScheduledJob(String schedule, String handler) {}

    /**
     * A component that talks to something outside the application's own code.
     *
     * @param detail the address or name of the other side when the code states it, or empty
     */
    public record Integration(String component, String technology, String detail) {}

    /**
     * How the application is secured.
     *
     * @param rules the access rules of every security filter chain, in the order they are tried
     * @param features what the configuration turns on or off, in plain words
     * @param methodRules how many classes and methods carry {@code @PreAuthorize} or a similar annotation
     */
    public record SecurityDesign(List<AccessRule> rules, List<String> features, int methodRules) {

        public SecurityDesign {
            rules = List.copyOf(rules);
            features = List.copyOf(features);
        }

        public boolean isEmpty() {
            return rules.isEmpty() && features.isEmpty() && methodRules == 0;
        }
    }

    public record AccessRule(String requests, String access, String definedIn) {}

    /**
     * How the application uses AI models.
     *
     * @param components classes that use a chat, embedding or image model, a vector store or chat memory
     * @param tools methods the model can call
     */
    public record AiDesign(
            List<AiComponent> components,
            List<AiTool> tools,
            List<String> advisors,
            List<String> vectorStores,
            List<String> models) {

        public AiDesign {
            components = List.copyOf(components);
            tools = List.copyOf(tools);
            advisors = List.copyOf(advisors);
            vectorStores = List.copyOf(vectorStores);
            models = List.copyOf(models);
        }

        public boolean isEmpty() {
            return components.isEmpty() && tools.isEmpty() && models.isEmpty() && vectorStores.isEmpty();
        }
    }

    public record AiComponent(String component, List<String> uses) {

        public AiComponent {
            uses = List.copyOf(uses);
        }
    }

    /**
     * A method a model can call.
     *
     * @param kind {@code Tool} for a Spring AI tool, {@code MCP tool} for one served over MCP
     */
    public record AiTool(String kind, String name, String description, String definedIn) {}

    /**
     * What happens after a request, message or timer reaches the application.
     *
     * @param trigger {@code HTTP}, {@code Message} or {@code Schedule}
     * @param direct the components the entry point uses
     * @param indirect the components those use in turn
     */
    public record Flow(String entry, String trigger, List<String> direct, List<String> indirect) {

        public Flow {
            direct = List.copyOf(direct);
            indirect = List.copyOf(indirect);
        }
    }

    /**
     * Facts from {@code application.properties} and {@code application.yml}. Only names, ports and product kinds
     * are kept, never addresses or credentials.
     */
    public record RuntimeSettings(
            Optional<String> applicationName,
            Optional<String> serverPort,
            List<String> dataStores,
            List<String> profiles,
            List<String> aiModels,
            List<String> vectorStores) {

        public RuntimeSettings {
            dataStores = List.copyOf(dataStores);
            profiles = List.copyOf(profiles);
            aiModels = List.copyOf(aiModels);
            vectorStores = List.copyOf(vectorStores);
        }

        public static RuntimeSettings none() {
            return new RuntimeSettings(Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of());
        }
    }
}
