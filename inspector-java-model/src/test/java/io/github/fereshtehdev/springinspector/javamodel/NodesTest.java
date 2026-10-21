package io.github.fereshtehdev.springinspector.javamodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.Test;

class NodesTest {

    private final CompilationUnit unit = StaticJavaParser.parse("class A { void m() { call(); } }");

    @Test
    void findsTheClosestEnclosingNodeOfAType() {
        MethodCallExpr call = unit.findFirst(MethodCallExpr.class).orElseThrow();

        assertThat(Nodes.ancestor(call, MethodDeclaration.class))
                .map(MethodDeclaration::getNameAsString)
                .contains("m");
        assertThat(Nodes.ancestor(call, ClassOrInterfaceDeclaration.class)).isPresent();
    }

    @Test
    void aNodeIsNotItsOwnAncestor() {
        MethodDeclaration method = unit.findFirst(MethodDeclaration.class).orElseThrow();

        assertThat(Nodes.ancestor(method, MethodDeclaration.class)).isEmpty();
    }
}
