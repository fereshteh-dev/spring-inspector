package io.github.fereshtehdev.springinspector.javamodel;

import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.PROJECT;
import static io.github.fereshtehdev.springinspector.javamodel.Fixtures.main;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import io.github.fereshtehdev.springinspector.core.domain.LineRange;
import io.github.fereshtehdev.springinspector.javamodel.Fixtures.Sources;
import io.github.fereshtehdev.springinspector.javamodel.patch.Patches;
import io.github.fereshtehdev.springinspector.javamodel.patch.TextEdit;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaFileTest {

    private static final Path FILE = main("com/acme/Service.java");

    private static final String SOURCE = """
            package com.acme;

            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.*;
            import static java.util.Objects.requireNonNull;

            @Service
            class Service {

                @Autowired
                private Repository repository;

                @Deprecated private String name;

                void run() {
                    requireNonNull(repository);
                }
            }
            """;

    private static JavaFile parse(String source) {
        JavaModel model = new JavaModel(PROJECT, new Sources().put(FILE, source));
        return new JavaFile(model.parse(FILE).orElseThrow(), new Resolver(), new DependencyRecorder());
    }

    private final JavaFile file = parse(SOURCE);

    private FieldDeclaration field(String name) {
        return file.unit()
                .findFirst(
                        FieldDeclaration.class,
                        field -> field.getVariable(0).getNameAsString().equals(name))
                .orElseThrow();
    }

    private String applied(TextEdit edit) {
        return Patches.apply(SOURCE, List.of(edit));
    }

    @Test
    void namesResolveThroughExplicitImports() {
        assertThat(file.refersTo("Autowired", "org.springframework.beans.factory.annotation.Autowired"))
                .isTrue();
        assertThat(file.refersTo("Autowired", "com.other.Autowired")).isFalse();
    }

    @Test
    void namesResolveThroughWildcardImports() {
        assertThat(file.refersTo("Service", "org.springframework.stereotype.Service"))
                .isTrue();
    }

    @Test
    void namesResolveToTypesOfTheSamePackage() {
        assertThat(file.refersTo("Repository", "com.acme.Repository")).isTrue();
    }

    @Test
    void staticImportsDoNotImportTypes() {
        assertThat(file.refersTo("Objects", "java.util.Objects")).isFalse();
    }

    @Test
    void qualifiedNamesAlwaysMatchAndOtherSimpleNamesNever() {
        assertThat(file.refersTo("java.util.List", "java.util.List")).isTrue();
        assertThat(file.refersTo("List", "java.util.Map")).isFalse();
    }

    @Test
    void typesInTheDefaultPackageNeedNoImport() {
        JavaFile defaultPackage = parse("class A { B b; }");

        assertThat(defaultPackage.refersTo("B", "B")).isTrue();
        assertThat(defaultPackage.refersTo("B", "com.acme.B")).isFalse();
    }

    @Test
    void findsAnnotationsByQualifiedName() {
        assertThat(file.hasAnnotation(field("repository"), "org.springframework.beans.factory.annotation.Autowired"))
                .isTrue();
        assertThat(file.hasAnnotation(field("name"), "org.springframework.beans.factory.annotation.Autowired"))
                .isFalse();
    }

    @Test
    void reportsTheLinesAndTextOfANode() {
        MethodDeclaration run = file.unit().findFirst(MethodDeclaration.class).orElseThrow();

        assertThat(file.lines(run)).isEqualTo(LineRange.of(15, 17));
        assertThat(file.location(run).file()).isEqualTo(FILE);
        assertThat(file.snippet(run)).startsWith("    void run() {").endsWith("    }");
        assertThat(file.text(run.getType())).isEqualTo("void");
        assertThat(file.indentation(run)).isEqualTo("    ");
        assertThat(file.lineSeparator()).isEqualTo("\n");
    }

    @Test
    void deletingAnAnnotationAloneOnItsLineRemovesTheLine() {
        AnnotationExpr autowired = field("repository").getAnnotation(0);

        assertThat(applied(file.delete(autowired))).contains("\n\n    private Repository repository;");
    }

    @Test
    void deletingAnAnnotationBeforeOtherCodeRemovesTheFollowingBlanks() {
        AnnotationExpr deprecated = field("name").getAnnotation(0);

        assertThat(applied(file.delete(deprecated))).contains("\n    private String name;");
    }

    @Test
    void insertsBeforeANodeAndAfterItsLine() {
        FieldDeclaration name = field("name");

        assertThat(applied(file.insertBefore(name.getElementType(), "final "))).contains("private final String name;");
        assertThat(applied(file.insertAfterLine(name, "    int added;\n")))
                .contains("private String name;\n    int added;\n");
    }

    @Test
    void replacesANode() {
        assertThat(applied(file.replace(field("name").getElementType(), "CharSequence")))
                .contains("private CharSequence name;");
    }

    @Test
    void buildsAPatchFromEdits() {
        String diff = file.patch(List.of(file.replace(field("name").getElementType(), "CharSequence")))
                .unifiedDiff();

        assertThat(diff)
                .contains("-    @Deprecated private String name;\n+    @Deprecated private CharSequence name;\n");
    }

    @Test
    void nodesWithoutAPositionCannotBeEdited() {
        assertThatThrownBy(() -> file.replace(new MarkerAnnotationExpr("Detached"), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(file.lines(new MarkerAnnotationExpr("Detached"))).isEqualTo(LineRange.line(1));
    }

    @Test
    void deletingTheLastNodeOfAFileWithoutFinalLineBreakDeletesToTheEnd() {
        JavaFile last = parse("class A {}");

        assertThat(Patches.apply("class A {}", List.of(last.delete(last.unit().getType(0)))))
                .isEmpty();
    }
}
