package com.flick.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits three competing models to a trace and picks the simplest one that is honest
 * about the motion.
 *
 * Selection is rule-based rather than a pure information criterion, because a
 * many-knot spline nearly always wins on raw error while being the least useful
 * answer. A developer wants a bezier whenever a bezier is good enough.
 */
public final class CurveFitter {
    private CurveFitter() {}

    public static final int SAMPLES = 48;
    private static final double BEZIER_GOOD_ENOUGH = 0.018;
    private static final double PATH_TARGET = 0.012;
    private static final int MAX_KNOTS = 10;

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double sq(double v) { return v * v; }

    public static double rmsOf(Easing e, Trace tr) {
        double s = 0;
        for (int i = 0; i < tr.t.length; i++) {
            double d = e.at(tr.t[i]) - tr.p[i];
            s += d * d;
        }
        return Math.sqrt(s / tr.t.length);
    }

    private static Bezier fitBezier(final Trace tr) {
        double[][] starts = {
            {0.25, 0.10, 0.25, 1.00},
            {0.42, 0.00, 0.58, 1.00},
            {0.00, 0.00, 0.58, 1.00},
            {0.50, 0.00, 0.50, 1.00},
            {0.34, 1.56, 0.64, 1.00},
            {0.36, -0.60, 0.66, 1.00},
        };
        Bezier best = null;
        double bestErr = Double.MAX_VALUE;
        for (double[] st : starts) {
            ObjFn fn = new ObjFn() {
                @Override public double f(double[] p) {
                    double x1 = clamp(p[0], 0, 1), x2 = clamp(p[2], 0, 1);
                    double y1 = clamp(p[1], -3, 4), y2 = clamp(p[3], -3, 4);
                    double pen = sq(p[0] - x1) + sq(p[2] - x2) + sq(p[1] - y1) + sq(p[3] - y2);
                    Bezier b = new Bezier(x1, y1, x2, y2);
                    double s = 0;
                    for (int i = 0; i < tr.t.length; i++) {
                        double d = b.at(tr.t[i]) - tr.p[i];
                        s += d * d;
                    }
                    return s / tr.t.length + pen * 10.0;
                }
            };
            double[] r = NelderMead.minimize(fn, st, 0.15, 400);
            Bezier b = new Bezier(clamp(r[0], 0, 1), clamp(r[1], -3, 4),
                                  clamp(r[2], 0, 1), clamp(r[3], -3, 4));
            double err = rmsOf(b, tr);
            if (err < bestErr) { bestErr = err; best = b; }
        }
        return best;
    }

    private static Spring fitSpring(final Trace tr) {
        final double durSec = Math.max(0.02, tr.durationMs / 1000.0);
        double[][] starts = {
            {5.7 / durSec, 0.70},
            {9.0 / durSec, 0.40},
            {14.0 / durSec, 0.22},
            {4.0 / durSec, 1.00},
        };
        Spring best = null;
        double bestErr = Double.MAX_VALUE;
        for (double[] st : starts) {
            ObjFn fn = new ObjFn() {
                @Override public double f(double[] p) {
                    double w = clamp(p[0], 0.5, 4000), z = clamp(p[1], 0.05, 3.0);
                    double pen = sq(p[0] - w) * 1e-4 + sq(p[1] - z);
                    Spring s = new Spring(w, z, durSec);
                    double acc = 0;
                    for (int i = 0; i < tr.t.length; i++) {
                        double d = s.at(tr.t[i]) - tr.p[i];
                        acc += d * d;
                    }
                    return acc / tr.t.length + pen * 10.0;
                }
            };
            double[] r = NelderMead.minimize(fn, st, Math.max(1.0, st[0] * 0.25), 400);
            Spring s = new Spring(clamp(r[0], 0.5, 4000), clamp(r[1], 0.05, 3.0), durSec);
            double err = rmsOf(s, tr);
            if (err < bestErr) { bestErr = err; best = s; }
        }
        return best;
    }

    /**
     * Knot positions at the motion's velocity extrema — the moments where it changed
     * its mind. Those are the points a chain of cubics has to be pinned to.
     */
    static int[] inflectionKnots(Trace tr) {
        int n = tr.p.length;
        double[] v = new double[n - 1];
        for (int i = 0; i < n - 1; i++) v[i] = tr.p[i + 1] - tr.p[i];

        // light smoothing so digitizer noise does not read as a beat
        double[] sv = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            double a = v[Math.max(0, i - 1)], b = v[i], c = v[Math.min(v.length - 1, i + 1)];
            sv[i] = (a + b + c) / 3.0;
        }

        List<Integer> knots = new ArrayList<Integer>();
        knots.add(0);
        for (int i = 1; i < sv.length - 1; i++) {
            boolean localMax = sv[i] > sv[i - 1] && sv[i] >= sv[i + 1];
            boolean localMin = sv[i] < sv[i - 1] && sv[i] <= sv[i + 1];
            if (localMax || localMin) {
                if (i - knots.get(knots.size() - 1) >= 3) knots.add(i);
            }
        }
        if (knots.get(knots.size() - 1) != n - 1) knots.add(n - 1);

        // keep it interpretable
        while (knots.size() > MAX_KNOTS) knots.remove(knots.size() / 2);

        int[] out = new int[knots.size()];
        for (int i = 0; i < out.length; i++) out[i] = knots.get(i);
        return out;
    }

    private static SplineEasing splineAt(Trace tr, int[] idx) {
        double[] xs = new double[idx.length], ys = new double[idx.length];
        for (int i = 0; i < idx.length; i++) {
            xs[i] = tr.t[idx[i]];
            ys[i] = tr.p[idx[i]];
        }
        xs[0] = 0; xs[xs.length - 1] = 1;
        for (int i = 1; i < xs.length; i++) {
            if (xs[i] <= xs[i - 1]) xs[i] = Math.min(1.0, xs[i - 1] + 1e-4);
        }
        return new SplineEasing(xs, ys);
    }

    private static SplineEasing uniformSpline(Trace tr, int knots) {
        double[] xs = new double[knots], ys = new double[knots];
        for (int i = 0; i < knots; i++) {
            xs[i] = i / (double) (knots - 1);
            ys[i] = tr.valueAt(xs[i]);
        }
        return new SplineEasing(xs, ys);
    }

    /**
     * Prefer knots at the real inflection points; fall back to uniform refinement when
     * that is not accurate enough. Smallest spline that hits the target wins.
     */
    private static SplineEasing fitPath(Trace tr) {
        SplineEasing best = null;
        double bestErr = Double.MAX_VALUE;

        try {
            SplineEasing infl = splineAt(tr, inflectionKnots(tr));
            double e = rmsOf(infl, tr);
            best = infl; bestErr = e;
            if (e <= PATH_TARGET) return infl;
        } catch (Exception ignored) { }

        int[] sizes = { 3, 4, 5, 6, 8, 10, 12 };
        for (int k : sizes) {
            SplineEasing s = uniformSpline(tr, k);
            double e = rmsOf(s, tr);
            if (e <= PATH_TARGET) {
                // only take the uniform one if it is not more complex than the inflection fit
                if (best == null || k <= best.knotCount() || bestErr > PATH_TARGET) return s;
                return best;
            }
            if (e < bestErr) { bestErr = e; best = s; }
        }
        return best != null ? best : uniformSpline(tr, 12);
    }

    public static FitResult fit(Trace input) {
        Trace tr = input.resample(SAMPLES);

        Bezier b = fitBezier(tr);
        double rb = rmsOf(b, tr);
        Spring s = fitSpring(tr);
        double rs = rmsOf(s, tr);
        SplineEasing p = fitPath(tr);
        double rp = rmsOf(p, tr);

        FitResult.Model chosen;
        String why;

        if (tr.hasOvershoot() && rs <= rb * 1.15) {
            chosen = FitResult.Model.SPRING;
            why = "You overshot and settled. A bezier cannot do that honestly, a spring can.";
        } else if (rb <= BEZIER_GOOD_ENOUGH) {
            chosen = FitResult.Model.BEZIER;
            why = "A single cubic bezier describes this within " + Fmt.f(rb * 100, 1) + "% error.";
        } else if (rs <= rb * 0.8 && rs <= 0.025) {
            chosen = FitResult.Model.SPRING;
            why = "Springy. The spring fits " + Fmt.f((1 - rs / rb) * 100, 0) + "% closer than a bezier.";
        } else {
            chosen = FitResult.Model.PATH;
            why = "Too much character for one curve, so it is kept as "
                    + p.segments() + " cubic segments.";
        }

        return new FitResult(tr, b, rb, s, rs, p, rp, chosen, why);
    }
}
