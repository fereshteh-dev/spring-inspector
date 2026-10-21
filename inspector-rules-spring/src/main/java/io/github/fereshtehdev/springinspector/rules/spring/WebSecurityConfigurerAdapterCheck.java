package io.github.fereshtehdev.springinspector.rules.spring;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.github.fereshtehdev.springinspector.core.domain.Category;
import io.github.fereshtehdev.springinspector.core.domain.Confidence;
import io.github.fereshtehdev.springinspector.core.domain.Finding;
import io.github.fereshtehdev.springinspector.core.domain.HowToFix;
import io.github.fereshtehdev.springinspector.core.domain.RuleId;
import io.github.fereshtehdev.springinspector.core.domain.Severity;
import io.github.fereshtehdev.springinspector.core.domain.WhyItMatters;
import io.github.fereshtehdev.springinspector.javamodel.JavaCheck;
import io.github.fereshtehdev.springinspector.javamodel.JavaFile;
import java.util.List;

/** Security configuration extending {@code WebSecurityConfigurerAdapter}, which Spring Security 6 removed. */
public final class WebSecurityConfigurerAdapterCheck extends JavaCheck {

    static final RuleId ID = RuleId.of("migration.web-security-configurer-adapter");

    private static final String ADAPTER =
            "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter";

    private static final WhyItMatters WHY = new WhyItMatters(
            "WebSecurityConfigurerAdapter was deprecated in Spring Security 5.7 and removed in 6.0, which Spring Boot 3"
                    + " uses. The configuration no longer compiles there and blocks the upgrade.",
            List.of(SpringCode.springDocs(
                    "Spring Security: Configuration without WebSecurityConfigurerAdapter",
                    "spring-security/reference/servlet/configuration/java.html")));

    private static final HowToFix FIX = HowToFix.withoutPatch(
            "Declare a @Bean SecurityFilterChain filterChain(HttpSecurity http) instead of overriding configure(...),"
                    + " and expose an AuthenticationManager or UserDetailsService bean if you configured one.");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Category category() {
        return Category.MIGRATION;
    }

    @Override
    protected List<Finding> inspect(JavaFile file) {
        return file
                .unit()
                .findAll(
                        ClassOrInterfaceDeclaration.class,
                        type -> type.getExtendedTypes().stream()
                                .anyMatch(extended -> file.refersTo(extended.getNameWithScope(), ADAPTER)))
                .stream()
                .map(type -> finding(
                        file,
                        type.getName(),
                        Severity.MAJOR,
                        Confidence.HIGH,
                        type.getNameAsString()
                                + " extends WebSecurityConfigurerAdapter, which Spring Security 6 removed",
                        WHY,
                        FIX))
                .toList();
    }
}
