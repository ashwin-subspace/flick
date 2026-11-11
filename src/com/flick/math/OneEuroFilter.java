package com.flick.math;

/**
 * One Euro filter (Casiez et al.). Smooths hard when the finger moves slowly and
 * barely at all when it moves fast, so hesitation is cleaned up while a dash stays crisp.
 */
public final class OneEuroFilter {
    private final double minCutoff, beta, dCutoff;
    private double xPrev, dxPrev, tPrev;
    private boolean started;

    public OneEuroFilter(double minCutoff, double beta, double dCutoff) {
        this.minCutoff = minCutoff;
        this.beta = beta;
        this.dCutoff = dCutoff;
    }

    public OneEuroFilter() { this(1.2, 0.010, 1.0); }

    private static double alpha(double cutoff, double dt) {
        double tau = 1.0 / (2.0 * Math.PI * cutoff);
        return 1.0 / (1.0 + tau / dt);
    }

    /** @param t seconds, must be increasing. */
    public double filter(double x, double t) {
        if (!started) {
            started = true;
            xPrev = x; dxPrev = 0; tPrev = t;
            return x;
        }
        double dt = t - tPrev;
        if (dt <= 1e-6) dt = 1e-6;
        tPrev = t;

        double dx = (x - xPrev) / dt;
        double edx = dxPrev + alpha(dCutoff, dt) * (dx - dxPrev);
        dxPrev = edx;

        double cutoff = minCutoff + beta * Math.abs(edx);
        double ex = xPrev + alpha(cutoff, dt) * (x - xPrev);
        xPrev = ex;
        return ex;
    }

    public void reset() { started = false; dxPrev = 0; }
}
