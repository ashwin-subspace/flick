package com.flick.math;

/**
 * An arbitrary curve stored as evenly spaced stops with linear interpolation between
 * them. This is exactly the shape of CSS linear(), so it exports losslessly, and it can
 * represent motion no single bezier or spring can (multiple beats, hesitate-dash-settle).
 */
public final class PolyEasing implements Easing {
    public final double[] stops;

    public PolyEasing(double[] stops) {
        if (stops == null || stops.length < 2) throw new IllegalArgumentException("need >= 2 stops");
        this.stops = stops.clone();
    }

    /** Resample any easing down to n evenly spaced stops. */
    public static PolyEasing sample(Easing e, int n) {
        double[] s = new double[n];
        for (int i = 0; i < n; i++) s[i] = e.at(i / (double) (n - 1));
        return new PolyEasing(s);
    }

    @Override public double at(double t) {
        if (t <= 0) return stops[0];
        if (t >= 1) return stops[stops.length - 1];
        double x = t * (stops.length - 1);
        int i = (int) Math.floor(x);
        if (i >= stops.length - 1) i = stops.length - 2;
        double f = x - i;
        return stops[i] * (1 - f) + stops[i + 1] * f;
    }

    public int paramCount() { return stops.length; }
}
