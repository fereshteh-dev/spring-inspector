package io.github.fereshtehdev.springinspector.javamodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StringExpressionsTest {

    private static CompilationUnit parse(String source) {
        return new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_25))
                .parse(source)
                .getResult()
                .orElseThrow();
    }

    /** The first argument of the {@code use(...)} call in a method with the given body. */
    private static Expression argument(String body) {
        return parse("class A { static final String T = \"t\"; void m(String id, String name) { " + body + " } }")
                .findFirst(MethodCallExpr.class, call -> call.getNameAsString().equals("use"))
                .orElseThrow()
                .getArgument(0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "use(\"select * from t where id = \" + id);",
                "use(String.format(\"select %s\", id));",
                "use(\"select %s\".formatted(name));",
                "use(\"select \".concat(id));",
                "String sql = \"select \" + id; use(sql);",
                "String where = \" where id = \" + id; String sql = \"select * from t\" + where; use(sql);",
                "use((\"a\" + (id)));",
                "use(\"a\" + this.name);"
            })
    void stringsBuiltFromValuesAreDetected(String body) {
        assertThat(StringExpressions.isBuiltFromValues(argument(body))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "use(\"select * from t\");",
                "use(\"select * from \" + T);",
                "use(\"a\" + 1 + 'c' + A.MAX_SIZE);",
                "use(\"\"\"\n select 1\n \"\"\");",
                "use(id);",
                "String table = \"users\"; use(\"select * from \" + table);",
                "use(other.format(\"%s\", id));"
            })
    void constantsAndValuesUsedAsTheyAreAreNotBuiltStrings(String body) {
        assertThat(StringExpressions.isBuiltFromValues(argument(body))).isFalse();
    }

    @Test
    void reportsTheRuntimeValuesAStringIsBuiltFrom() {
        Expression built = argument("use(\"select \" + id + T + name.trim());");

        assertThat(StringExpressions.runtimeValues(built))
                .extracting(Node::toString)
                .containsExactly("id", "name.trim()");
    }

    @Test
    void aReassignedLocalIsNotFollowed() {
        Expression built = argument("String sql = \"select 1\"; sql = sql + id; use(sql);");

        assertThat(StringExpressions.followLocal(built).toString()).isEqualTo("sql");
    }

    @Test
    void collectsLiteralTextThroughLocals() {
        Expression built = argument("String where = \"where id = \" + id; use(\"select * from t \" + where);");

        assertThat(StringExpressions.literalText(built)).isEqualTo("select * from t  where id = ");
    }

    @Test
    void namesOutsideAMethodAreNotFollowed() {
        Expression field = parse("class A { String a = b; }")
                .findFirst(com.github.javaparser.ast.expr.NameExpr.class)
                .orElseThrow();

        assertThat(StringExpressions.followLocal(field)).isSameAs(field);
    }
}
