package com.acme.shapes;

class Shapes {

    double area(Shape shape) {
        return switch (shape) {
            case Circle c -> Math.PI * c.r() * c.r();
            case Square s -> s.side() * s.side();
            case Rectangle r -> r.w() * r.h();
        };
    }

    String describe(Object value) {
        if (value instanceof String text) {
            return text;
        } else if (value instanceof Number number) {
            return number.toString();
        }
        return "?";
    }
}
