package io.github.fereshtehdev.springinspector.archtests;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class HexagonalRulesTest {

    private static final String LEAKY_ROOT = "io.github.fereshtehdev.springinspector.archtests.fixtures.leaky";
    private static final String UNMARKED_ROOT = "io.github.fereshtehdev.springinspector.archtests.fixtures.unmarked";

    private static final JavaClasses LEAKY_CLASSES = new ClassFileImporter().importPackages(LEAKY_ROOT);

    @Test
    void rejectsCoreClassUsingAThirdPartyLibrary() {
        assertThatThrownBy(() -> HexagonalRules.coreDependsOnlyOnTheJdkAndJSpecify(LEAKY_ROOT)
                        .check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ThirdPartyCore");
    }

    @Test
    void rejectsCoreClassUsingTheJdkHttpClient() {
        assertThatThrownBy(() ->
                        HexagonalRules.coreUsesNoJdkHttpOrDesktopUi(LEAKY_ROOT).check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HttpCallingCore");
    }

    @Test
    void rejectsCoreClassReferencingAnAdapter() {
        assertThatThrownBy(
                        () -> HexagonalRules.coreDependsOnNoAdapter(LEAKY_ROOT).check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AdapterAwareCore");
    }

    @Test
    void rejectsCheckModuleCallingAnotherCheckModule() {
        assertThatThrownBy(() ->
                        HexagonalRules.checkModulesAreIndependent(LEAKY_ROOT).check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SecurityCheck");
    }

    @Test
    void rejectsCheckCallingAnotherCheckInTheSameModule() {
        assertThatThrownBy(() ->
                        HexagonalRules.checksDoNotCallEachOther(LEAKY_ROOT).check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DelegatingCheck");
    }

    @Test
    void rejectsJavaModelClassUsingAThirdPartyLibrary() {
        assertThatThrownBy(() -> HexagonalRules.javaModelDependsOnlyOnCoreAndTheParser(LEAKY_ROOT)
                        .check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ThirdPartyModel");
    }

    @Test
    void rejectsCheckUsingAThirdPartyLibrary() {
        assertThatThrownBy(() -> HexagonalRules.checksDependOnlyOnCoreAndTheJavaModel(LEAKY_ROOT)
                        .check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ThirdPartyCheck");
    }

    @Test
    void rejectsCheckResolvingTypesThroughJavaParserDirectly() {
        assertThatThrownBy(() -> HexagonalRules.checksResolveTypesOnlyThroughTheJavaModel(LEAKY_ROOT)
                        .check(LEAKY_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ResolvingCheck");
    }

    @Test
    void rejectsPackageWithoutNullMarked() {
        JavaClasses unmarked = new ClassFileImporter().importPackages(UNMARKED_ROOT);

        assertThatThrownBy(() ->
                        HexagonalRules.everyPackageIsNullMarked(UNMARKED_ROOT).check(unmarked))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("has no @NullMarked package-info");
    }
}
