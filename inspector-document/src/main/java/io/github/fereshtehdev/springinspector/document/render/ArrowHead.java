package io.github.fereshtehdev.springinspector.document.render;

import io.github.fereshtehdev.springinspector.document.render.DiagramLayout.Arrow;

/** The triangle at the end of an arrow, pointing the way the curve arrives. */
final class ArrowHead {

    private static final double LENGTH = 8;
    private static final double HALF_WIDTH = 3.2;

    private ArrowHead() {}

    /** Three points as {@code x, y} pairs: the tip first, then the two back corners. */
    static double[] of(Arrow arrow) {
        double fromX = arrow.controlX2();
        double fromY = arrow.controlY2();
        if (Math.hypot(arrow.x2() - fromX, arrow.y2() - fromY) < 0.5) {
            fromX = arrow.x1();
            fromY = arrow.y1();
        }
        double length = Math.max(0.5, Math.hypot(arrow.x2() - fromX, arrow.y2() - fromY));
        double alongX = (arrow.x2() - fromX) / length;
        double alongY = (arrow.y2() - fromY) / length;
        double backX = arrow.x2() - alongX * LENGTH;
        double backY = arrow.y2() - alongY * LENGTH;
        return new double[] {
            arrow.x2(),
            arrow.y2(),
            backX - alongY * HALF_WIDTH,
            backY + alongX * HALF_WIDTH,
            backX + alongY * HALF_WIDTH,
            backY - alongX * HALF_WIDTH
        };
    }
}
