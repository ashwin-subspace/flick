package com.flick;

import com.flick.math.Bezier;
import com.flick.math.CurveFitter;
import com.flick.math.FitResult;
import com.flick.math.SplineEasing;
import com.flick.math.Spring;
import com.flick.math.Trace;

/** One curve in the library, with enough stored to rebuild the full fit. */
public final class SavedCurve {
    public long id = -1;
    public String name = "";
    public String tags = "";
    public boolean favourite = false;
    public String model = "BEZIER";
    public double x1, y1, x2, y2;
    public double omega, zeta;
    public double durationMs;
    public double[] traceStops = new double[0];
    public long created;

    public static SavedCurve from(String name, String tags, FitResult f) {
        SavedCurve c = new SavedCurve();
        c.name = name;
        c.tags = tags == null ? "" : tags;
        c.model = f.model.name();
        c.x1 = f.bezier.x1; c.y1 = f.bezier.y1;
        c.x2 = f.bezier.x2; c.y2 = f.bezier.y2;
        c.omega = f.spring.omega; c.zeta = f.spring.zeta;
        c.durationMs = f.durationMs();
        // Store the trace itself so the curve view and previews stay faithful.
        int n = 32;
        c.traceStops = new double[n];
        for (int i = 0; i < n; i++) c.traceStops[i] = f.trace.valueAt(i / (double) (n - 1));
        c.created = System.currentTimeMillis();
        return c;
    }

    /** Everything this curve can be matched against in the library search box. */
    public boolean matches(String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase();
        if (q.length() == 0) return true;
        return name.toLowerCase().contains(q)
                || tags.toLowerCase().contains(q)
                || model.toLowerCase().contains(q);
    }

    /** Rebuild a FitResult without re-running the optimizer. */
    public FitResult toFit() {
        int n = Math.max(2, traceStops.length);
        double[] t = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i / (double) (n - 1);
            p[i] = traceStops.length > i ? traceStops[i] : t[i];
        }
        Trace tr = new Trace(t, p, Math.max(1, durationMs));

        Bezier b = new Bezier(x1, y1, x2, y2);
        Spring s = new Spring(omega, zeta, Math.max(0.02, durationMs / 1000.0));

        int knots = Math.max(2, Math.min(10, n));
        double[] xs = new double[knots], ys = new double[knots];
        for (int i = 0; i < knots; i++) {
            xs[i] = i / (double) (knots - 1);
            ys[i] = tr.valueAt(xs[i]);
        }
        SplineEasing sp = new SplineEasing(xs, ys);

        FitResult.Model m;
        try { m = FitResult.Model.valueOf(model); }
        catch (Exception e) { m = FitResult.Model.BEZIER; }

        FitResult f = new FitResult(tr, b, CurveFitter.rmsOf(b, tr), s, CurveFitter.rmsOf(s, tr),
                sp, CurveFitter.rmsOf(sp, tr), m, "Saved curve");
        f.durationOverrideMs = durationMs;
        return f;
    }

    public static String encode(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    public static double[] decode(String s) {
        if (s == null || s.length() == 0) return new double[0];
        String[] parts = s.split(",");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { v[i] = Double.parseDouble(parts[i]); }
            catch (NumberFormatException e) { v[i] = 0; }
        }
        return v;
    }
}
