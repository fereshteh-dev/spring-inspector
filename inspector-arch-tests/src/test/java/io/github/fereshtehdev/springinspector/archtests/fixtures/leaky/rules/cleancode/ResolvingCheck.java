package io.github.fereshtehdev.springinspector.archtests.fixtures.leaky.rules.cleancode;

import com.github.javaparser.ast.expr.Expression;

public final class ResolvingCheck {

    public String typeOf(Expression expression) {
        return expression.calculateResolvedType().describe();
    }
}
