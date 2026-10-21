package com.acme.shapes;

class Shapes {

    double area(Shape shape) {
        if (shape instanceof Circle c) {
            return Math.PI * c.r() * c.r();
        } else if (shape instanceof Square s) {
            return s.side() * s.side();
        } else if (shape instanceof Rectangle r) {
            return r.w() * r.h();
        }
        throw new IllegalArgumentException("Unknown shape " + shape);
    }
}
