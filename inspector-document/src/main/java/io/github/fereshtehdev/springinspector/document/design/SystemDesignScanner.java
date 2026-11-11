package io.github.fereshtehdev.springinspector.document.design;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.fereshtehdev.springinspector.core.domain.architecture.ImportGraph;
import io.github.fereshtehdev.springinspector.core.domain.change.SourceFile;
import io.github.fereshtehdev.springinspector.document.design.Flows.Wiring;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AccessRule;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiComponent;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiDesign;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.AiTool;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Endpoint;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.EntityModel;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Integration;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.Listener;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.RuntimeSettings;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.ScheduledJob;
import io.github.fereshtehdev.springinspector.document.design.SystemDesign.SecurityDesign;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads a project's system design from its source files. The code is only parsed, never compiled or run, and a
 * file that cannot be parsed is skipped: the document then says less, but is never wrong about what it does say.
 */
public final class SystemDesignScanner {

    private SystemDesignScanner() {}

    /**
     * Reads the design from files already in memory.
     *
     * @param graph the project's classes and imports
     * @param javaSources the Java files of the application, without its tests
     * @param settingsFiles its {@code application*.properties} and {@code application*.yml} files
     */
    public static SystemDesign scan(ImportGraph graph, List<SourceFile> javaSources, List<SourceFile> settingsFiles) {
        Facts facts = javaSources.parallelStream()
                .sorted(Comparator.comparing(source -> source.path().toString()))
                .map(SystemDesignScanner::read)
                .reduce(new Facts(), Facts::plus);
        RuntimeSettings settings = SettingsReader.of(settingsFiles);
        Set<String> stored = new HashSet<>();
        facts.entities.forEach(entity -> stored.add(entity.name()));
        Set<String> vectorStores = new TreeSet<>(facts.vectorStores);
        vectorStores.addAll(settings.vectorStores());
        return new SystemDesign(
                facts.applications.stream().sorted().toList(),
                Technologies.components(graph),
                Technologies.of(graph),
                facts.endpoints.stream()
                        .sorted(Comparator.comparing(Endpoint::path).thenComparing(Endpoint::method))
                        .toList(),
                facts.entities.stream()
                        .sorted(Comparator.comparing(EntityModel::name))
                        .toList(),
                facts.listeners,
                facts.jobs,
                facts.integrations,
                new SecurityDesign(
                        facts.rules, facts.securityFeatures.stream().distinct().toList(), facts.methodRules),
                new AiDesign(
                        facts.aiComponents,
                        facts.tools,
                        List.copyOf(new TreeSet<>(facts.advisors)),
                        List.copyOf(vectorStores),
                        settings.aiModels()),
                Flows.of(facts.wirings, stored),
                settings);
    }

    private static Facts read(SourceFile source) {
        Facts facts = new Facts();
        Optional<CompilationUnit> unit = parse(source.content());
        if (unit.isEmpty()) {
            return facts;
        }
        for (TypeDeclaration<?> type : unit.get().getTypes()) {
            read(unit.get(), type, facts, true);
        }
        return facts;
    }

    private static void read(CompilationUnit unit, TypeDeclaration<?> type, Facts facts, boolean topLevel) {
        Set<String> referenced = new HashSet<>();
        type.findAll(ClassOrInterfaceType.class).forEach(used -> referenced.add(used.getNameAsString()));
        if (Annotations.has(type, "SpringBootApplication")) {
            facts.applications.add(type.getNameAsString());
        }
        List<Endpoint> endpoints = Endpoints.of(type);
        List<Listener> listeners = Triggers.listeners(type);
        List<ScheduledJob> jobs = Triggers.jobs(type);
        facts.endpoints.addAll(endpoints);
        facts.listeners.addAll(listeners);
        facts.jobs.addAll(jobs);
        Entities.of(type).ifPresent(facts.entities::add);
        facts.rules.addAll(SecurityReader.rules(type));
        facts.tools.addAll(AiReader.tools(type));
        if (topLevel) {
            // These look at everything inside the type, nested types included, so they run once per file type.
            facts.integrations.addAll(Integrations.of(type, referenced));
            facts.securityFeatures.addAll(SecurityReader.features(type));
            facts.methodRules += SecurityReader.methodRules(type);
            AiReader.component(type, referenced).ifPresent(facts.aiComponents::add);
            facts.advisors.addAll(AiReader.advisors(referenced));
            facts.vectorStores.addAll(AiReader.vectorStores(referenced));
            String trigger = !endpoints.isEmpty() ? "HTTP" : !listeners.isEmpty() ? "Message" : "";
            facts.wirings.add(Flows.wiring(unit, type, trigger.isEmpty() && !jobs.isEmpty() ? "Schedule" : trigger));
        }
        type.getMembers().forEach(member -> {
            if (member instanceof TypeDeclaration<?> nested) {
                read(unit, nested, facts, false);
            }
        });
    }

    private static Optional<CompilationUnit> parse(String content) {
        try {
            ParserConfiguration configuration = new ParserConfiguration()
                    .setLanguageLevel(LanguageLevel.BLEEDING_EDGE)
                    .setAttributeComments(false);
            return new JavaParser(configuration).parse(content).getResult();
        } catch (RuntimeException | StackOverflowError unparseable) {
            return Optional.empty();
        }
    }

    /** What the files read so far say; combined in file order, so the result is the same on every run. */
    private static final class Facts {

        final List<String> applications = new ArrayList<>();
        final List<Endpoint> endpoints = new ArrayList<>();
        final List<EntityModel> entities = new ArrayList<>();
        final List<Listener> listeners = new ArrayList<>();
        final List<ScheduledJob> jobs = new ArrayList<>();
        final List<Integration> integrations = new ArrayList<>();
        final List<AccessRule> rules = new ArrayList<>();
        final List<String> securityFeatures = new ArrayList<>();
        final List<AiComponent> aiComponents = new ArrayList<>();
        final List<AiTool> tools = new ArrayList<>();
        final List<String> advisors = new ArrayList<>();
        final List<String> vectorStores = new ArrayList<>();
        final List<Wiring> wirings = new ArrayList<>();
        int methodRules;

        Facts plus(Facts other) {
            Facts sum = new Facts();
            for (Facts part : List.of(this, other)) {
                sum.applications.addAll(part.applications);
                sum.endpoints.addAll(part.endpoints);
                sum.entities.addAll(part.entities);
                sum.listeners.addAll(part.listeners);
                sum.jobs.addAll(part.jobs);
                sum.integrations.addAll(part.integrations);
                sum.rules.addAll(part.rules);
                sum.securityFeatures.addAll(part.securityFeatures);
                sum.aiComponents.addAll(part.aiComponents);
                sum.tools.addAll(part.tools);
                sum.advisors.addAll(part.advisors);
                sum.vectorStores.addAll(part.vectorStores);
                sum.wirings.addAll(part.wirings);
                sum.methodRules += part.methodRules;
            }
            return sum;
        }
    }
}
