package com.flick.math;

/**
 * A captured gesture, normalized to progress-over-time. t runs 0..1; p is 0 at the
 * start and 1 at the end but may dip below 0 (anticipation) or above 1 (overshoot).
 */
public final class Trace {
    public final double[] t, p;
    public final double durationMs;

    public double anticipation;      // depth below 0, as a fraction
    public double anticipationMs;    // when the pull-back bottoms out
    public double overshoot;         // height above 1, as a fraction
    public double overshootMs;
    public double settleMs;          // from peak until it stays within 2% of 1
    public double backLoad;          // fraction of distance covered in the last 40% of time

    public Trace(double[] t, double[] p, double durationMs) {
        this.t = t; this.p = p; this.durationMs = durationMs;
        analyze();
    }

    private void analyze() {
        int n = p.length;
        int minI = 0, maxI = 0;
        for (int i = 0; i < n; i++) {
            if (p[i] < p[minI]) minI = i;
            if (p[i] > p[maxI]) maxI = i;
        }
        if (p[minI] < -0.005) {
            anticipation = -p[minI];
            anticipationMs = t[minI] * durationMs;
        }
        if (p[maxI] > 1.005) {
            overshoot = p[maxI] - 1;
            overshootMs = t[maxI] * durationMs;
            double settleT = t[n - 1];
            for (int i = maxI; i < n; i++) {
                boolean stays = true;
                for (int j = i; j < n; j++) {
                    if (Math.abs(p[j] - 1) > 0.02) { stays = false; break; }
                }
                if (stays) { settleT = t[i]; break; }
            }
            settleMs = (settleT - t[maxI]) * durationMs;
        }
        backLoad = 1.0 - valueAt(0.6);
    }

    /** Linear interpolation of p at normalized time x. */
    public double valueAt(double x) {
        if (x <= t[0]) return p[0];
        int n = t.length;
        if (x >= t[n - 1]) return p[n - 1];
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (t[mid] <= x) lo = mid; else hi = mid;
        }
        double span = t[hi] - t[lo];
        if (span < 1e-12) return p[lo];
        double f = (x - t[lo]) / span;
        return p[lo] * (1 - f) + p[hi] * f;
    }

    /** Evenly spaced resample, which makes fitting far better conditioned. */
    public Trace resample(int n) {
        double[] nt = new double[n], np = new double[n];
        for (int i = 0; i < n; i++) {
            nt[i] = i / (double) (n - 1);
            np[i] = valueAt(nt[i]);
        }
        return new Trace(nt, np, durationMs);
    }

    public boolean hasAnticipation() { return anticipation > 0.02; }
    public boolean hasOvershoot() { return overshoot > 0.02; }
}
