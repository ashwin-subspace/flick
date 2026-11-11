package com.flick.math;

/**
 * A chain of cubic segments through knots placed at the motion's velocity inflection
 * points. This is the model for motion no single bezier or spring can hold: hesitate,
 * dash, settle.
 *
 * Stored as a cubic Hermite spline (knot positions plus tangents) because that converts
 * exactly to a chain of cubic beziers, which is what Android's PathInterpolator and
 * After Effects both actually want. x is strictly increasing by construction, so the
 * curve is always a valid easing function.
 */
public final class SplineEasing implements Easing {

    public final double[] xs, ys, ms;

    public SplineEasing(double[] xs, double[] ys) {
        if (xs == null || ys == null || xs.length < 2 || xs.length != ys.length) {
            throw new IllegalArgumentException("need >= 2 matched knots");
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.ms = new double[xs.length];
        computeTangents();
    }

    /** Central differences at interior knots, one-sided at the ends. */
    private void computeTangents() {
        int n = xs.length;
        if (n == 2) {
            double m = (ys[1] - ys[0]) / (xs[1] - xs[0]);
            ms[0] = m; ms[1] = m;
            return;
        }
        ms[0] = (ys[1] - ys[0]) / (xs[1] - xs[0]);
        ms[n - 1] = (ys[n - 1] - ys[n - 2]) / (xs[n - 1] - xs[n - 2]);
        for (int i = 1; i < n - 1; i++) {
            ms[i] = (ys[i + 1] - ys[i - 1]) / (xs[i + 1] - xs[i - 1]);
        }
    }

    private int segmentFor(double x) {
        int lo = 0, hi = xs.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) lo = mid; else hi = mid;
        }
        return lo;
    }

    @Override public double at(double x) {
        if (x <= xs[0]) return ys[0];
        if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
        int i = segmentFor(x);
        double h = xs[i + 1] - xs[i];
        if (h < 1e-12) return ys[i];
        double t = (x - xs[i]) / h;
        double t2 = t * t, t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        return h00 * ys[i] + h10 * h * ms[i] + h01 * ys[i + 1] + h11 * h * ms[i + 1];
    }

    public int knotCount() { return xs.length; }

    public int segments() { return xs.length - 1; }

    /**
     * Exact Hermite-to-bezier conversion, as SVG path data for
     * android:pathData on a pathInterpolator.
     */
    public String toPathData() {
        StringBuilder sb = new StringBuilder();
        sb.append("M ").append(Fmt.n(xs[0])).append(",").append(Fmt.n(ys[0]));
        for (int i = 0; i < xs.length - 1; i++) {
            double h = xs[i + 1] - xs[i];
            double c1x = xs[i] + h / 3.0;
            double c1y = ys[i] + ms[i] * h / 3.0;
            double c2x = xs[i + 1] - h / 3.0;
            double c2y = ys[i + 1] - ms[i + 1] * h / 3.0;
            sb.append(" C ").append(Fmt.n(c1x)).append(",").append(Fmt.n(c1y))
              .append(" ").append(Fmt.n(c2x)).append(",").append(Fmt.n(c2y))
              .append(" ").append(Fmt.n(xs[i + 1])).append(",").append(Fmt.n(ys[i + 1]));
        }
        return sb.toString();
    }

    /** Number of velocity sign changes across the knots, i.e. how many beats it has. */
    public int beats() {
        int b = 1;
        double prev = 0;
        boolean have = false;
        for (int i = 1; i < ys.length; i++) {
            double d = ys[i] - ys[i - 1];
            if (Math.abs(d) < 1e-4) continue;
            if (have && Math.signum(d) != Math.signum(prev)) b++;
            prev = d;
            have = true;
        }
        return Math.max(1, b);
    }
}
