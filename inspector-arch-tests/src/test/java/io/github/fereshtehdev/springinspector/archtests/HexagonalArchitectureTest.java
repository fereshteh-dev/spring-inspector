package io.github.fereshtehdev.springinspector.archtests;

import static io.github.fereshtehdev.springinspector.archtests.HexagonalRules.checkModulesAreIndependent;
import static io.github.fereshtehdev.springinspector.archtests.HexagonalRules.checksDoNotCallEachOther;
import static io.github.fereshtehdev.springinspector.archtests.HexagonalRules.coreDependsOnNoAdapter;
import static io.github.fereshtehdev.springinspector.archtests.HexagonalRules.coreUsesNoJdkHttpOrDesktopUi;
import static io.github.fereshtehdev.springinspector.archtests.HexagonalRules.everyPackageIsNullMarked;
import static io.github.fereshtehdev.springinspector.archtests.HexagonalRules.nothingUsesJackson2;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String ROOT = "io.github.fereshtehdev.springinspector";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void coreDependsOnlyOnTheJdkAndJSpecify() {
        HexagonalRules.coreDependsOnlyOnTheJdkAndJSpecify(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void coreDoesNotUseJdkHttpOrDesktopUiApis() {
        coreUsesNoJdkHttpOrDesktopUi(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void coreDoesNotDependOnAnyAdapter() {
        coreDependsOnNoAdapter(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void checkModulesDoNotDependOnEachOther() {
        checkModulesAreIndependent(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void checksDoNotCallOtherChecks() {
        checksDoNotCallEachOther(ROOT).allowEmptyShould(true).check(PRODUCTION_CLASSES);
    }

    @Test
    void noModuleUsesJackson2() {
        nothingUsesJackson2(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void everyProductionPackageIsNullMarked() {
        everyPackageIsNullMarked(ROOT).check(PRODUCTION_CLASSES);
    }
}
