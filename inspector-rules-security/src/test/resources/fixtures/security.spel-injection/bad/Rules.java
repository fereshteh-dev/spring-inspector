package com.acme;

import org.springframework.expression.ExpressionParser;
import org.springframework.web.bind.annotation.*;

class Rules {
    Object evaluate(ExpressionParser parser, @RequestParam String rule) {
        return parser.parseExpression(rule).getValue();
    }
}
