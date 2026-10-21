package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.Reference;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.core.domain.project.Applicability;
import io.github.fereshtehdev.springinspector.core.domain.project.Version;
import io.github.fereshtehdev.springinspector.core.domain.project.VersionRange;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Jackson 2 ({@code com.fasterxml.jackson}) imports in a Spring Boot 4 project, which is built on Jackson 3
 * ({@code tools.jackson}). {@code com.fasterxml.jackson.annotation} is shared by both and is fine.
 */
public final class Jackson2InBoot4Check extends JavaCheck {

    static final RuleId ID = RuleId.of("spring.jackson2-in-boot4");

    private static final String JACKSON_2 = "com.fasterxml.jackson.";
    private static final String SHARED_ANNOTATIONS = "com.fasterxml.jackson.annotation.";
    private static final String JACKSON_3 = "tools.jackson.";

    /** Jackson 2 types that exist under the same name in Jackson 3, checked against jackson-databind 3.1. */
    private static final Set<String> SAME_NAME_IN_JACKSON_3 = Set.of(
            "com.fasterxml.jackson.databind.ObjectMapper",
            "com.fasterxml.jackson.databind.json.JsonMapper",
            "com.fasterxml.jackson.databind.DeserializationFeature",
            "com.fasterxml.jackson.databind.SerializationFeature",
            "com.fasterxml.jackson.databind.MapperFeature",
            "com.fasterxml.jackson.databind.JavaType",
            "com.fasterxml.jackson.databind.PropertyNamingStrategies",
            "com.fasterxml.jackson.core.type.TypeReference");

    /** ObjectMapper methods that Jackson 3 removed: its mappers are immutable and configured through builders. */
    private static final Set<String> REMOVED_MAPPER_METHODS = Set.of(
            "configure",
            "enable",
            "disable",
            "registerModule",
            "registerModules",
            "findAndRegisterModules",
            "addMixIn",
            "setSerializationInclusion",
            "setDefaultPropertyInclusion",
            "setPropertyNamingStrategy",
            "setVisibility",
            "setDateFormat",
            "setTimeZone",
            "setLocale",
            "setAnnotationIntrospector",
            "setDefaultTyping",
            "activateDefaultTyping",
            "setFilterProvider",
            "setInjectableValues",
            "setMixIns",
            "setNodeFactory",
            "setTypeFactory",
            "setHandlerInstantiator",
            "setDefaultPrettyPrinter",
            "setSubtypeResolver",
            "setConfig",
            "copy");

    /** Jackson 3 throws unchecked exceptions, so catching these checked ones would no longer compile. */
    private static final Set<String> CHECKED_JACKSON_EXCEPTIONS =
            Set.of("IOException", "JsonProcessingException", "JsonMappingException", "JsonParseException");

    private static final WhyItMatters WHY = new WhyItMatters(
            "Spring Boot 4 auto-configures Jackson 3 (tools.jackson). Jackson 2 types neither receive Boot's"
                    + " JSON configuration nor match the mapper Spring MVC and RestClient use, so serialization"
                    + " silently differs between your code and the framework.",
            List.of(new Reference(
                    "Spring Boot 4.0 Migration Guide",
                    URI.create(
                            "https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide"))));

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.SPRING_BOOT;
    }

    @Override
    public Applicability applicability() {
        return Applicability.always().requiringSpringBoot(VersionRange.atLeast(Version.of(4, 0, 0)));
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        List<ImportDeclaration> jackson2 = file.unit().getImports().stream()
                .filter(declaration -> declaration.getNameAsString().startsWith(JACKSON_2)
                        && !declaration.getNameAsString().startsWith(SHARED_ANNOTATIONS))
                .toList();
        boolean patchable = isPatchable(file, jackson2);
        List<Finding> findings = new ArrayList<>();
        for (ImportDeclaration declaration : jackson2) {
            String jackson3 = JACKSON_3 + declaration.getNameAsString().substring(JACKSON_2.length());
            String explanation = "Import " + jackson3 + " instead. Jackson 3 configures mappers through"
                    + " JsonMapper.builder() and throws the unchecked JacksonException.";
            HowToFix fix = patchable
                    ? HowToFix.withPatch(
                            explanation, file.patch(List.of(file.replace(declaration.getName(), jackson3))))
                    : HowToFix.withoutPatch(explanation + " Some types were renamed; see the migration guide.");
            findings.add(finding(
                    file,
                    declaration,
                    Severity.MAJOR,
                    Confidence.HIGH,
                    "Jackson 2 type " + declaration.getNameAsString() + " is used in a Spring Boot 4 project",
                    WHY,
                    fix));
        }
        return findings;
    }

    /**
     * Rewriting imports is only safe when every Jackson 2 type kept its name and the file relies on nothing Jackson 3
     * removed: mutating the mapper, or catching the checked exceptions Jackson 2 threw.
     */
    private static boolean isPatchable(JavaFile file, List<ImportDeclaration> jackson2) {
        boolean sameNames = jackson2.stream()
                .allMatch(declaration -> !declaration.isAsterisk()
                        && !declaration.isStatic()
                        && SAME_NAME_IN_JACKSON_3.contains(declaration.getNameAsString()));
        boolean mutatesMapper = file.unit()
                .findFirst(MethodCallExpr.class, call -> REMOVED_MAPPER_METHODS.contains(call.getNameAsString()))
                .isPresent();
        boolean catchesCheckedJacksonExceptions = file.unit()
                .findFirst(
                        CatchClause.class,
                        clause -> CHECKED_JACKSON_EXCEPTIONS.stream()
                                .anyMatch(clause.getParameter().getType().asString()::contains))
                .isPresent();
        boolean qualifiedUse = file.source()
                .content()
                .lines()
                .anyMatch(
                        line -> line.contains(JACKSON_2) && !line.stripLeading().startsWith("import "));
        return sameNames && !mutatesMapper && !catchesCheckedJacksonExceptions && !qualifiedUse;
    }
}
