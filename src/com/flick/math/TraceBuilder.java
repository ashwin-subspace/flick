package com.flick.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw touch samples into a normalized Trace.
 *
 * Progress is the projection of the finger onto the straight line from where the
 * gesture started to where it ended. That keeps a diagonal or curved swipe working,
 * and — unlike arc length — lets a pull-back read as negative progress, which is what
 * makes anticipation detectable.
 */
public final class TraceBuilder {
    public static final int MIN_SAMPLES = 8;
    public static final double MIN_DISTANCE_PX = 40.0;
    public static final double MIN_DURATION_MS = 60.0;

    private final List<double[]> raw = new ArrayList<double[]>(); // x, y, tSeconds
    private final OneEuroFilter fx = new OneEuroFilter();
    private final OneEuroFilter fy = new OneEuroFilter();

    public void reset() { raw.clear(); fx.reset(); fy.reset(); }

    /** @param tSec monotonically increasing timestamp in seconds. */
    public void add(double x, double y, double tSec) {
        if (!raw.isEmpty()) {
            double prevT = raw.get(raw.size() - 1)[2];
            if (tSec <= prevT) tSec = prevT + 1e-4;
        }
        raw.add(new double[]{ fx.filter(x, tSec), fy.filter(y, tSec), tSec });
    }

    public int size() { return raw.size(); }

    /** Raw (filtered) points, for drawing the live trail. */
    public List<double[]> points() { return raw; }

    public String rejectReason() {
        if (raw.size() < MIN_SAMPLES) return "Too few points — flick a bit slower";
        double[] a = raw.get(0), z = raw.get(raw.size() - 1);
        double dx = z[0] - a[0], dy = z[1] - a[1];
        if (Math.hypot(dx, dy) < MIN_DISTANCE_PX) return "Too short — drag further";
        if ((z[2] - a[2]) * 1000.0 < MIN_DURATION_MS) return "Too fast — take a moment longer";
        return null;
    }

    public Trace build() {
        if (rejectReason() != null) return null;

        double[] a = raw.get(0), z = raw.get(raw.size() - 1);
        double dx = z[0] - a[0], dy = z[1] - a[1];
        double len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;

        double t0 = a[2], t1 = z[2];
        double dur = t1 - t0;

        int n = raw.size();
        double[] ts = new double[n], ps = new double[n];
        for (int i = 0; i < n; i++) {
            double[] s = raw.get(i);
            ps[i] = ((s[0] - a[0]) * ux + (s[1] - a[1]) * uy) / len;
            ts[i] = (s[2] - t0) / dur;
        }
        ts[0] = 0; ts[n - 1] = 1;
        for (int i = 1; i < n; i++) if (ts[i] <= ts[i - 1]) ts[i] = Math.min(1.0, ts[i - 1] + 1e-6);

        return new Trace(ts, ps, dur * 1000.0);
    }
}
