package io.github.fereshtehdev.springinspector.archtests;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.github.fereshtehdev.springinspector.core.port.Check;
import org.jspecify.annotations.NullMarked;

final class HexagonalRules {

    private static final String[] JDK_HTTP_AND_DESKTOP_UI_PACKAGES = {
        "java.net.http..", "java.awt..", "javax.swing..", "java.applet.."
    };

    private static final String[] JACKSON_2_PACKAGES = {
        "com.fasterxml.jackson.core..",
        "com.fasterxml.jackson.databind..",
        "com.fasterxml.jackson.dataformat..",
        "com.fasterxml.jackson.datatype..",
        "com.fasterxml.jackson.module.."
    };

    private HexagonalRules() {}

    static ArchRule coreDependsOnlyOnTheJdkAndJSpecify(String root) {
        return classes()
                .that()
                .resideInAPackage(core(root))
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "org.jspecify..", core(root))
                .because("inspector-core is pure Java; libraries and frameworks belong in adapters");
    }

    static ArchRule coreUsesNoJdkHttpOrDesktopUi(String root) {
        return noClasses()
                .that()
                .resideInAPackage(core(root))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(JDK_HTTP_AND_DESKTOP_UI_PACKAGES)
                .because("HTTP and UI are adapter concerns");
    }

    static ArchRule coreDependsOnNoAdapter(String root) {
        return noClasses()
                .that()
                .resideInAPackage(core(root))
                .should()
                .dependOnClassesThat(resideInAPackage(root + "..").and(not(resideInAPackage(core(root)))))
                .because("adapters depend on core, never the reverse");
    }

    static ArchRule checkModulesAreIndependent(String root) {
        return slices().matching(root + ".rules.(*)..")
                .should()
                .notDependOnEachOther()
                .because("checks are plugins and never call each other");
    }

    static ArchRule checksDoNotCallEachOther(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .and()
                .implement(Check.class)
                .should(dependOnNoOtherCheck())
                .because("checks are plugins and never call each other");
    }

    static ArchRule nothingUsesJackson2(String root) {
        return noClasses()
                .that()
                .resideInAPackage(root + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(JACKSON_2_PACKAGES)
                .because("the project uses Jackson 3 (tools.jackson)");
    }

    static ArchRule everyPackageIsNullMarked(String root) {
        return classes().that().resideInAPackage(root + "..").should(resideInANullMarkedPackage());
    }

    private static String core(String root) {
        return root + ".core..";
    }

    private static ArchCondition<JavaClass> dependOnNoOtherCheck() {
        return new ArchCondition<>("depend on no other check") {
            @Override
            public void check(JavaClass check, ConditionEvents events) {
                for (Dependency dependency : check.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.equals(check) && !target.isInterface() && target.isAssignableTo(Check.class)) {
                        events.add(SimpleConditionEvent.violated(check, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> resideInANullMarkedPackage() {
        return new ArchCondition<>("reside in a package annotated with @NullMarked") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean nullMarked = javaClass.getPackage().isAnnotatedWith(NullMarked.class);
                String message = javaClass.getPackageName() + " has no @NullMarked package-info";
                events.add(new SimpleConditionEvent(javaClass, nullMarked, message));
            }
        };
    }
}
