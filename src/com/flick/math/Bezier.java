package com.flick.math;

/**
 * CSS-style cubic-bezier(x1,y1,x2,y2) easing. Endpoints are fixed at (0,0) and (1,1).
 * x(t) is inverted with Newton-Raphson, falling back to bisection when the
 * derivative is degenerate.
 */
public final class Bezier implements Easing {
    public final double x1, y1, x2, y2;
    private final double ax, bx, cx, ay, by, cy;

    public Bezier(double x1, double y1, double x2, double y2) {
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        cx = 3.0 * x1;
        bx = 3.0 * (x2 - x1) - cx;
        ax = 1.0 - cx - bx;
        cy = 3.0 * y1;
        by = 3.0 * (y2 - y1) - cy;
        ay = 1.0 - cy - by;
    }

    private double sampleX(double t) { return ((ax * t + bx) * t + cx) * t; }
    private double sampleY(double t) { return ((ay * t + by) * t + cy) * t; }
    private double sampleDX(double t) { return (3.0 * ax * t + 2.0 * bx) * t + cx; }

    /** Solve x(t) = x for t. */
    public double solveT(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double t = x;
        for (int i = 0; i < 8; i++) {
            double err = sampleX(t) - x;
            if (Math.abs(err) < 1e-7) return t;
            double d = sampleDX(t);
            if (Math.abs(d) < 1e-7) break;
            t -= err / d;
        }
        double lo = 0, hi = 1;
        t = x;
        for (int i = 0; i < 40; i++) {
            double v = sampleX(t);
            if (Math.abs(v - x) < 1e-7) return t;
            if (v > x) hi = t; else lo = t;
            t = (lo + hi) / 2.0;
        }
        return t;
    }

    @Override public double at(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return sampleY(solveT(t));
    }

    /** True when the curve leaves the 0..1 band, i.e. anticipation or overshoot. */
    public boolean isOutOfBand() { return y1 < 0 || y2 > 1 || y1 > 1 || y2 < 0; }

    // ---- editor refinements -------------------------------------------------

    private static double round05(double v) { return Math.round(v * 20.0) / 20.0; }

    /** Round every control point to the nearest 0.05, the numbers people actually write. */
    public Bezier snapped() {
        return new Bezier(Math.max(0, Math.min(1, round05(x1))), round05(y1),
                          Math.max(0, Math.min(1, round05(x2))), round05(y2));
    }

    /**
     * Force point symmetry about the curve's midpoint, so ease-in and ease-out match.
     * Averages the two handles rather than favouring either.
     */
    public Bezier mirrored() {
        double nx1 = (x1 + (1 - x2)) / 2.0;
        double ny1 = (y1 + (1 - y2)) / 2.0;
        return new Bezier(Math.max(0, Math.min(1, nx1)), ny1,
                          Math.max(0, Math.min(1, 1 - nx1)), 1 - ny1);
    }

    /** Pull anticipation and overshoot back inside 0..1. */
    public Bezier clamped() {
        return new Bezier(x1, Math.max(0, Math.min(1, y1)),
                          x2, Math.max(0, Math.min(1, y2)));
    }

    @Override public String toString() {
        return "cubic-bezier(" + Fmt.n(x1) + ", " + Fmt.n(y1) + ", " + Fmt.n(x2) + ", " + Fmt.n(y2) + ")";
    }
}
