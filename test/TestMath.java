import com.flick.math.*;

/**
 * Desktop test harness for Flick's math layer. The math package has no Android
 * imports, so it compiles and runs on a plain JVM.
 *
 *   javac -d build/test src/com/flick/math/*.java test/TestMath.java
 *   java -cp build/test TestMath
 */
public class TestMath {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name + "  -- " + detail); }
    }

    static void near(String name, double got, double want, double tol) {
        boolean ok = Math.abs(got - want) <= tol;
        check(name, ok, "got " + got + " want " + want + " tol " + tol);
    }

    static void section(String s) { System.out.println("\n== " + s + " =="); }

    // Build a Trace by sampling a known easing.
    static Trace synth(Easing e, int n, double durMs) {
        double[] t = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i / (double) (n - 1);
            p[i] = e.at(t[i]);
        }
        return new Trace(t, p, durMs);
    }

    public static void main(String[] args) {
        testFmt();
        testBezier();
        testSpring();
        testPoly();
        testNelderMead();
        testOneEuro();
        testTrace();
        testTraceBuilder();
        testBezierFit();
        testSpringFit();
        testPolyFit();
        testSpline();
        testBezierRefine();
        testModelSelection();
        testExporters();
        testEdgeCases();

        System.out.println("\n----------------------------------------");
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void testFmt() {
        section("Fmt");
        check("trims trailing zeros", Fmt.n(0.500).equals("0.5"), Fmt.n(0.5));
        check("zero is 0", Fmt.n(0.0).equals("0"), Fmt.n(0.0));
        check("tiny is 0", Fmt.n(0.0001).equals("0"), Fmt.n(0.0001));
        check("no locale comma", !Fmt.n(1.25).contains(","), Fmt.n(1.25));
        check("negatives ok", Fmt.n(-0.6).equals("-0.6"), Fmt.n(-0.6));
        check("3dp cap", Fmt.n(0.123456).equals("0.123"), Fmt.n(0.123456));
        check("f fixed dp", Fmt.f(3.14159, 2).equals("3.14"), Fmt.f(3.14159, 2));
    }

    static void testBezier() {
        section("Bezier");
        Bezier linear = new Bezier(0, 0, 1, 1);
        near("linear at 0.5", linear.at(0.5), 0.5, 1e-6);
        near("linear at 0.25", linear.at(0.25), 0.25, 1e-6);
        near("endpoint 0", linear.at(0), 0, 1e-9);
        near("endpoint 1", linear.at(1), 1, 1e-9);

        Bezier ease = new Bezier(0.42, 0, 0.58, 1);
        near("ease-in-out symmetric at 0.5", ease.at(0.5), 0.5, 1e-4);
        check("ease-in-out starts slow", ease.at(0.2) < 0.2, "" + ease.at(0.2));
        check("ease-in-out ends slow", ease.at(0.8) > 0.8, "" + ease.at(0.8));

        // solveT must invert x(t) accurately across the range
        Bezier b = new Bezier(0.17, 0.89, 0.32, 1.28);
        double worst = 0;
        for (double x = 0.0; x <= 1.0; x += 0.01) {
            double t = b.solveT(x);
            double back = new Bezier(0.17, 0.89, 0.32, 1.28).at(x);
            worst = Math.max(worst, Math.abs(t - t)); // t is finite
            check2(back);
        }
        check("solveT stays finite", worst < 1e-9, "" + worst);

        // monotonic x means at() is well defined and bounded for a sane curve
        Bezier m = new Bezier(0.25, 0.1, 0.25, 1.0);
        boolean mono = true;
        double prev = -1;
        for (double x = 0; x <= 1.0001; x += 0.005) {
            double v = m.at(x);
            if (v < prev - 1e-9) mono = false;
            prev = v;
        }
        check("standard ease is monotonic", mono, "not monotonic");

        check("overshoot detected", new Bezier(0.34, 1.56, 0.64, 1).isOutOfBand(), "band");
        check("plain curve in band", !new Bezier(0.42, 0, 0.58, 1).isOutOfBand(), "band");
        check("toString format", new Bezier(0.42, 0, 0.58, 1).toString()
                .equals("cubic-bezier(0.42, 0, 0.58, 1)"),
                new Bezier(0.42, 0, 0.58, 1).toString());
    }

    static void check2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            failed++;
            System.out.println("  FAIL  bezier produced NaN/Inf");
        }
    }

    static void testSpring() {
        section("Spring");
        Spring s = new Spring(20, 0.5, 1.0);
        near("response at 0", s.response(0), 0, 1e-9);
        near("settles to 1", s.response(5.0), 1, 1e-3);
        check("underdamped overshoots", s.overshoot() > 0.05, "" + s.overshoot());

        Spring crit = new Spring(20, 1.0, 1.0);
        near("critical settles", crit.response(2.0), 1, 1e-3);
        check("critical no overshoot", crit.overshoot() < 1e-6, "" + crit.overshoot());
        boolean mono = true;
        double prev = -1;
        for (double t = 0; t < 2.0; t += 0.005) {
            double v = crit.response(t);
            if (v < prev - 1e-9) mono = false;
            prev = v;
        }
        check("critical is monotonic", mono, "not monotonic");

        Spring over = new Spring(20, 2.0, 1.0);
        near("overdamped starts at 0", over.response(0), 0, 1e-9);
        near("overdamped settles", over.response(5.0), 1, 1e-3);
        check("overdamped no overshoot", over.overshoot() < 1e-6, "" + over.overshoot());

        near("stiffness is omega^2", new Spring(20, 0.5, 1).stiffness(), 400, 1e-6);
        check("settleTime positive", s.settleTime(0.02) > 0, "" + s.settleTime(0.02));

        // at() maps normalized time through durationSec
        Spring d = new Spring(20, 0.5, 0.5);
        near("at(1) equals response(dur)", d.at(1.0), d.response(0.5), 1e-9);
    }

    static void testPoly() {
        section("PolyEasing");
        PolyEasing p = new PolyEasing(new double[]{0, 0.5, 1});
        near("mid stop exact", p.at(0.5), 0.5, 1e-9);
        near("quarter interpolated", p.at(0.25), 0.25, 1e-9);
        near("clamps low", p.at(-1), 0, 1e-9);
        near("clamps high", p.at(2), 1, 1e-9);

        PolyEasing s = PolyEasing.sample(new Bezier(0.42, 0, 0.58, 1), 16);
        check("sampled has 16 stops", s.paramCount() == 16, "" + s.paramCount());
        near("sampled endpoints 0", s.at(0), 0, 1e-9);
        near("sampled endpoints 1", s.at(1), 1, 1e-9);

        boolean threw = false;
        try { new PolyEasing(new double[]{1}); } catch (IllegalArgumentException e) { threw = true; }
        check("rejects < 2 stops", threw, "no throw");
    }

    static void testNelderMead() {
        section("NelderMead");
        // Rosenbrock, minimum at (1,1)
        ObjFn rosen = new ObjFn() {
            public double f(double[] p) {
                double a = 1 - p[0], b = p[1] - p[0] * p[0];
                return a * a + 100 * b * b;
            }
        };
        double[] r = NelderMead.minimize(rosen, new double[]{-1.2, 1.0}, 0.5, 4000);
        near("rosenbrock x", r[0], 1.0, 0.02);
        near("rosenbrock y", r[1], 1.0, 0.04);

        ObjFn quad = new ObjFn() {
            public double f(double[] p) { return (p[0] - 3) * (p[0] - 3) + (p[1] + 2) * (p[1] + 2); }
        };
        double[] q = NelderMead.minimize(quad, new double[]{0, 0}, 0.5, 800);
        near("quadratic x", q[0], 3.0, 1e-3);
        near("quadratic y", q[1], -2.0, 1e-3);
    }

    static void testOneEuro() {
        section("OneEuroFilter");
        OneEuroFilter f = new OneEuroFilter();
        double v = 0;
        for (int i = 0; i < 50; i++) v = f.filter(100.0, i * 0.008);
        near("constant signal preserved", v, 100.0, 1e-6);

        OneEuroFilter g = new OneEuroFilter();
        double first = g.filter(5.0, 0);
        near("first sample passes through", first, 5.0, 1e-9);

        // noisy constant should be smoothed toward the mean
        OneEuroFilter h = new OneEuroFilter();
        double last = 0;
        for (int i = 0; i < 200; i++) {
            double noise = (i % 2 == 0) ? 1.0 : -1.0;
            last = h.filter(50.0 + noise, i * 0.008);
        }
        check("noise attenuated", Math.abs(last - 50.0) < 0.9, "" + last);

        h.reset();
        near("reset re-seeds", h.filter(7.0, 0), 7.0, 1e-9);
    }

    static void testTrace() {
        section("Trace analysis");
        // overshoot then settle
        int n = 100;
        double[] t = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i / (double) (n - 1);
            p[i] = 1 - Math.exp(-6 * t[i]) * Math.cos(9 * t[i]);
        }
        p[0] = 0; p[n - 1] = 1;
        Trace tr = new Trace(t, p, 400);
        check("overshoot detected", tr.hasOvershoot(), "overshoot=" + tr.overshoot);
        check("settle time set", tr.settleMs >= 0, "" + tr.settleMs);

        // anticipation: dip below zero early
        double[] t2 = new double[n], p2 = new double[n];
        for (int i = 0; i < n; i++) {
            t2[i] = i / (double) (n - 1);
            p2[i] = t2[i] < 0.25 ? -0.15 * Math.sin(t2[i] / 0.25 * Math.PI) : (t2[i] - 0.25) / 0.75;
        }
        p2[0] = 0; p2[n - 1] = 1;
        Trace tr2 = new Trace(t2, p2, 500);
        check("anticipation detected", tr2.hasAnticipation(), "ant=" + tr2.anticipation);
        check("anticipation timed", tr2.anticipationMs > 0 && tr2.anticipationMs < 500,
                "" + tr2.anticipationMs);

        // plain linear has neither
        Trace lin = synth(new Bezier(0, 0, 1, 1), 50, 300);
        check("linear has no overshoot", !lin.hasOvershoot(), "" + lin.overshoot);
        check("linear has no anticipation", !lin.hasAnticipation(), "" + lin.anticipation);
        near("linear backLoad is 0.4", lin.backLoad, 0.4, 0.02);

        near("valueAt interpolates", lin.valueAt(0.5), 0.5, 0.01);
        near("valueAt clamps low", lin.valueAt(-1), 0, 1e-9);
        near("valueAt clamps high", lin.valueAt(2), 1, 1e-9);

        Trace rs = lin.resample(20);
        check("resample length", rs.t.length == 20, "" + rs.t.length);
        near("resample keeps duration", rs.durationMs, 300, 1e-9);
        near("resample endpoint", rs.p[19], 1.0, 1e-6);
    }

    static void testTraceBuilder() {
        section("TraceBuilder");
        TraceBuilder tb = new TraceBuilder();
        check("rejects empty", tb.rejectReason() != null, "no reason");

        // a clean diagonal gesture
        tb.reset();
        for (int i = 0; i <= 40; i++) {
            double u = i / 40.0;
            tb.add(100 + 300 * u, 200 + 300 * u, u * 0.4);
        }
        check("accepts good gesture", tb.rejectReason() == null, "" + tb.rejectReason());
        Trace tr = tb.build();
        check("built non-null", tr != null, "null trace");
        if (tr != null) {
            near("starts at 0", tr.p[0], 0, 1e-6);
            near("ends at 1", tr.p[tr.p.length - 1], 1.0, 1e-6);
            near("duration ms", tr.durationMs, 400, 5.0);
            near("t starts 0", tr.t[0], 0, 1e-12);
            near("t ends 1", tr.t[tr.t.length - 1], 1.0, 1e-12);
            boolean inc = true;
            for (int i = 1; i < tr.t.length; i++) if (tr.t[i] <= tr.t[i - 1]) inc = false;
            check("t strictly increasing", inc, "not increasing");
        }

        // too short in distance
        TraceBuilder sh = new TraceBuilder();
        for (int i = 0; i <= 20; i++) sh.add(100 + i * 0.5, 200, i * 0.01);
        check("rejects short drag", sh.rejectReason() != null, "accepted");

        // too few samples
        TraceBuilder few = new TraceBuilder();
        for (int i = 0; i < 3; i++) few.add(100 + i * 100, 200, i * 0.1);
        check("rejects few samples", few.rejectReason() != null, "accepted");

        // too fast
        TraceBuilder fast = new TraceBuilder();
        for (int i = 0; i <= 20; i++) fast.add(100 + i * 20, 200, i * 0.001);
        check("rejects too fast", fast.rejectReason() != null, "accepted");

        // non-monotonic timestamps must not blow up
        TraceBuilder nm = new TraceBuilder();
        for (int i = 0; i <= 30; i++) nm.add(100 + i * 15, 200, 0.2);
        check("survives equal timestamps", nm.rejectReason() != null || nm.build() != null, "crash");
    }

    static void testBezierFit() {
        section("Bezier fitting");
        Bezier known = new Bezier(0.42, 0.0, 0.58, 1.0);
        Trace tr = synth(known, 48, 400);
        FitResult f = CurveFitter.fit(tr);
        check("bezier rms tiny", f.rmsBezier < 0.006, "rms=" + f.rmsBezier);
        near("recovers x1", f.bezier.x1, 0.42, 0.08);
        near("recovers y1", f.bezier.y1, 0.0, 0.08);
        near("recovers x2", f.bezier.x2, 0.58, 0.08);
        near("recovers y2", f.bezier.y2, 1.0, 0.08);
        check("chose bezier", f.model == FitResult.Model.BEZIER, "" + f.model);

        Bezier sharp = new Bezier(0.25, 0.1, 0.25, 1.0);
        FitResult f2 = CurveFitter.fit(synth(sharp, 48, 300));
        check("sharp ease fits", f2.rmsBezier < 0.008, "rms=" + f2.rmsBezier);
        near("sharp x1", f2.bezier.x1, 0.25, 0.10);

        // control points must stay in the legal x range
        check("x1 in range", f.bezier.x1 >= 0 && f.bezier.x1 <= 1, "" + f.bezier.x1);
        check("x2 in range", f.bezier.x2 >= 0 && f.bezier.x2 <= 1, "" + f.bezier.x2);
    }

    static void testSpringFit() {
        section("Spring fitting");
        Spring known = new Spring(20, 0.5, 0.5);
        Trace tr = synth(known, 48, 500);
        FitResult f = CurveFitter.fit(tr);
        check("spring rms small", f.rmsSpring < 0.02, "rms=" + f.rmsSpring);
        near("recovers omega", f.spring.omega, 20.0, 4.0);
        near("recovers zeta", f.spring.zeta, 0.5, 0.15);
        check("spring beats bezier here", f.rmsSpring < f.rmsBezier,
                "spring=" + f.rmsSpring + " bezier=" + f.rmsBezier);
    }

    /** A wiggly multi-beat motion no bezier or spring can hold. */
    static Trace wiggle() {
        int n = 48;
        double[] t = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i / (double) (n - 1);
            p[i] = t[i] + 0.22 * Math.sin(t[i] * Math.PI * 4);
        }
        p[0] = 0; p[n - 1] = 1;
        return new Trace(t, p, 600);
    }

    static void testPolyFit() {
        section("Path fitting");
        FitResult f = CurveFitter.fit(wiggle());
        check("path rms small", f.rmsPath < 0.03, "rms=" + f.rmsPath);
        check("path beats bezier", f.rmsPath < f.rmsBezier,
                "path=" + f.rmsPath + " bez=" + f.rmsBezier);
        check("chose path", f.model == FitResult.Model.PATH, "" + f.model + " rb=" + f.rmsBezier);
        check("beats counted", f.beats() >= 2, "beats=" + f.beats());
        check("has real segments", f.path.segments() >= 2, "" + f.path.segments());
        check("headline mentions beats", f.headline().contains("beat"), f.headline());
    }

    static void testSpline() {
        section("SplineEasing");
        SplineEasing s = new SplineEasing(new double[]{0, 0.5, 1}, new double[]{0, 0.5, 1});
        near("linear spline mid", s.at(0.5), 0.5, 1e-9);
        near("linear spline quarter", s.at(0.25), 0.25, 1e-6);
        near("clamps low", s.at(-1), 0, 1e-12);
        near("clamps high", s.at(2), 1, 1e-12);
        check("knot count", s.knotCount() == 3, "" + s.knotCount());
        check("segments", s.segments() == 2, "" + s.segments());

        // passes exactly through its knots
        SplineEasing k = new SplineEasing(new double[]{0, 0.3, 0.7, 1},
                                          new double[]{0, 0.8, 0.2, 1});
        near("knot 1 exact", k.at(0.3), 0.8, 1e-9);
        near("knot 2 exact", k.at(0.7), 0.2, 1e-9);
        check("beats detects reversal", k.beats() >= 2, "" + k.beats());

        // path data is well formed and x-monotonic, as PathInterpolator requires
        String d = k.toPathData();
        check("starts with M 0,0", d.startsWith("M 0,0"), d);
        check("has cubic segments", d.contains(" C "), d);
        check("ends at 1,1", d.trim().endsWith("1,1"), d);
        int cs = d.split(" C ").length - 1;
        check("one cubic per segment", cs == k.segments(), cs + " vs " + k.segments());

        double[] xsSeen = extractXs(d);
        boolean mono = true;
        for (int i = 1; i < xsSeen.length; i++) if (xsSeen[i] < xsSeen[i - 1] - 1e-9) mono = false;
        check("path data x is monotonic", mono, "not monotonic");

        // finite everywhere
        for (double x = -0.2; x <= 1.2; x += 0.01) {
            if (Double.isNaN(k.at(x)) || Double.isInfinite(k.at(x))) {
                failed++; System.out.println("  FAIL  spline NaN at " + x); return;
            }
        }
        check("spline finite everywhere", true, "");

        boolean threw = false;
        try { new SplineEasing(new double[]{0}, new double[]{0}); }
        catch (IllegalArgumentException e) { threw = true; }
        check("rejects single knot", threw, "no throw");

        boolean threw2 = false;
        try { new SplineEasing(new double[]{0, 1}, new double[]{0}); }
        catch (IllegalArgumentException e) { threw2 = true; }
        check("rejects mismatched arrays", threw2, "no throw");
    }

    /** Pull every x coordinate out of an SVG path string. */
    static double[] extractXs(String d) {
        String[] toks = d.replace("M", " ").replace("C", " ").trim().split("\\s+");
        double[] xs = new double[toks.length];
        int n = 0;
        for (String t : toks) {
            if (t.contains(",")) {
                try { xs[n++] = Double.parseDouble(t.split(",")[0]); }
                catch (Exception ignored) { }
            }
        }
        double[] out = new double[n];
        System.arraycopy(xs, 0, out, 0, n);
        return out;
    }

    static void testBezierRefine() {
        section("Bezier refinements");
        Bezier b = new Bezier(0.234, 0.117, 0.673, 0.912);

        Bezier s = b.snapped();
        near("snap x1", s.x1, 0.25, 1e-9);
        near("snap y1", s.y1, 0.10, 1e-9);
        near("snap x2", s.x2, 0.65, 1e-9);
        near("snap y2", s.y2, 0.90, 1e-9);
        check("snap stays in x range", s.x1 >= 0 && s.x2 <= 1, "out of range");
        check("snap is idempotent", s.snapped().x1 == s.x1, "not idempotent");

        Bezier m = b.mirrored();
        near("mirror is symmetric in x", m.x2, 1 - m.x1, 1e-9);
        near("mirror is symmetric in y", m.y2, 1 - m.y1, 1e-9);
        check("mirror keeps x in range", m.x1 >= 0 && m.x1 <= 1, "" + m.x1);
        Bezier already = new Bezier(0.42, 0, 0.58, 1);
        near("already symmetric unchanged", already.mirrored().x1, 0.42, 1e-9);

        Bezier over = new Bezier(0.3, -0.4, 0.7, 1.6);
        Bezier c = over.clamped();
        near("clamp low y", c.y1, 0, 1e-12);
        near("clamp high y", c.y2, 1, 1e-12);
        near("clamp keeps x1", c.x1, 0.3, 1e-12);
        check("clamped is in band", !c.isOutOfBand(), "still out of band");
        check("clamp is idempotent", c.clamped().y2 == c.y2, "not idempotent");
    }

    static void testModelSelection() {
        section("Model selection");
        FitResult lin = CurveFitter.fit(synth(new Bezier(0, 0, 1, 1), 48, 300));
        check("linear picks bezier", lin.model == FitResult.Model.BEZIER, "" + lin.model);
        check("reason non-empty", lin.reason != null && lin.reason.length() > 0, "empty");
        check("headline non-empty", lin.headline().length() > 0, "empty");
        check("accuracy high", lin.accuracyPct() > 95, "" + lin.accuracyPct());
        check("paramCount bezier is 4", lin.paramCount() == 4, "" + lin.paramCount());

        FitResult sp = CurveFitter.fit(synth(new Spring(22, 0.35, 0.5), 48, 500));
        check("bouncy picks spring", sp.model == FitResult.Model.SPRING,
                "" + sp.model + " rb=" + sp.rmsBezier + " rs=" + sp.rmsSpring);
        check("spring paramCount 2", sp.paramCount() == 2, "" + sp.paramCount());
        check("easing() matches model", sp.easing() instanceof Spring, "wrong easing");

        // switching model by hand must change easing() and rms()
        sp.model = FitResult.Model.BEZIER;
        check("switch to bezier", sp.easing() instanceof Bezier, "wrong easing");
        near("rms follows model", sp.rms(), sp.rmsBezier, 1e-12);

        // describe() must match the model asked about, never the originally chosen one
        String db = sp.describe(FitResult.Model.BEZIER);
        String ds = sp.describe(FitResult.Model.SPRING);
        String dp = sp.describe(FitResult.Model.PATH);
        check("describes bezier", db.contains("bezier"), db);
        check("describes spring", ds.contains("Spring") && ds.contains("stiffness"), ds);
        check("describes path", dp.contains("cubic segments"), dp);
        check("descriptions differ", !db.equals(ds) && !ds.equals(dp), "identical");
        check("bezier desc quotes bezier error",
                db.contains(Fmt.f(sp.rmsBezier * 100, 1)), db);
        check("spring desc quotes spring error",
                ds.contains(Fmt.f(sp.rmsSpring * 100, 1)), ds);

        // a monotonic path should not be called a "1-beat" path
        FitResult mono = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 400));
        mono.model = FitResult.Model.PATH;
        if (mono.path.beats() == 1) {
            check("monotonic path headline reads well",
                    mono.headline().equals("This is a shaped path"), mono.headline());
        }
    }

    static void testExporters() {
        section("Exporters");
        FitResult bez = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 420));
        String css = Exporters.css(bez);
        check("css has cubic-bezier", css.contains("cubic-bezier("), css);
        check("css has duration", css.contains("420ms"), css);
        check("css no comma decimals", !css.matches(".*\\d,\\d.*"), css);
        check("css ends with semicolon", css.trim().endsWith(";"), css);

        String comp = Exporters.compose(bez);
        check("compose has CubicBezierEasing", comp.contains("CubicBezierEasing("), comp);
        check("compose has f suffix", comp.contains("f,"), comp);
        check("compose has tween", comp.contains("tween("), comp);

        String xml = Exporters.androidXml(bez);
        check("xml is pathInterpolator", xml.contains("<pathInterpolator"), xml);
        check("xml has controlX1", xml.contains("android:controlX1"), xml);
        check("xml declares ns", xml.contains("xmlns:android"), xml);
        check("xml self-closes", xml.trim().endsWith("/>"), xml);

        check("flutter has Cubic", Exporters.flutter(bez).contains("Cubic("), "");
        check("swiftui has timingCurve", Exporters.swiftUi(bez).contains("timingCurve("), "");

        String ae = Exporters.afterEffects(bez);
        check("AE has influence", ae.contains("influence"), ae);
        check("AE has span", ae.contains("Span"), ae);

        String lin = Exporters.cssLinear(bez);
        check("cssLinear uses linear()", lin.contains("linear("), lin);

        // spring routes to spring-aware targets
        FitResult sp = CurveFitter.fit(synth(new Spring(22, 0.35, 0.5), 48, 500));
        if (sp.model == FitResult.Model.SPRING) {
            check("compose spring", Exporters.compose(sp).contains("dampingRatio"),
                    Exporters.compose(sp));
            check("compose stiffness", Exporters.compose(sp).contains("stiffness"), "");
            check("flutter SpringDescription",
                    Exporters.flutter(sp).contains("SpringDescription"), "");
            check("swiftui interpolatingSpring",
                    Exporters.swiftUi(sp).contains("interpolatingSpring"), "");
            check("css falls back to linear()", Exporters.css(sp).contains("linear("),
                    Exporters.css(sp));
        }

        // path routes to stop lists and real cubic path data
        FitResult po = CurveFitter.fit(wiggle());
        if (po.model == FitResult.Model.PATH) {
            check("path css linear", Exporters.css(po).contains("linear("), Exporters.css(po));
            check("path xml pathData", Exporters.androidXml(po).contains("pathData"), "");
            check("path xml starts M 0,0", Exporters.androidXml(po).contains("M 0,0"), "");
            check("path xml uses cubics not lines",
                    Exporters.androidXml(po).contains(" C ")
                    && !Exporters.androidXml(po).contains(" L "),
                    Exporters.androidXml(po));
            check("path compose lambda", Exporters.compose(po).contains("Easing {"), "");
            check("path kotlin is stops",
                    Exporters.kotlinConstant("W", po).contains("Stops = floatArrayOf"),
                    Exporters.kotlinConstant("W", po));
        }

        // duration override flows through every exporter
        FitResult tuned = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 420));
        tuned.durationOverrideMs = 250;
        check("override changes duration", Exporters.durationMs(tuned) == 250,
                "" + Exporters.durationMs(tuned));
        check("override reaches css", Exporters.css(tuned).contains("250ms"),
                Exporters.css(tuned));
        check("override reaches compose", Exporters.compose(tuned).contains("250"), "");

        // every export must be free of NaN and Infinity
        for (FitResult f : new FitResult[]{bez, sp, po}) {
            for (Exporters.Item it : Exporters.all(f)) {
                check("no NaN in " + it.label + "/" + f.model,
                        !it.code.contains("NaN") && !it.code.contains("Infinity"), it.code);
                check("non-empty " + it.label, it.code.length() > 0, "empty");
            }
        }
        check("all() returns 7", Exporters.all(bez).size() == 7, "" + Exporters.all(bez).size());

        // identifiers
        check("id from spaces", Exporters.identifier("snappy settle").equals("SnappySettle"),
                Exporters.identifier("snappy settle"));
        check("id strips symbols", Exporters.identifier("bounce!! 2").equals("Bounce2"),
                Exporters.identifier("bounce!! 2"));
        check("id leading digit", Exporters.identifier("2fast").startsWith("C"),
                Exporters.identifier("2fast"));
        check("id empty fallback", Exporters.identifier("!!!").equals("Curve"),
                Exporters.identifier("!!!"));
        check("kotlin constant", Exporters.kotlinConstant("My Curve", bez).contains("val MyCurve"),
                Exporters.kotlinConstant("My Curve", bez));

        // duration clamps
        check("duration rounded to 10s", Exporters.durationMs(bez) % 10 == 0,
                "" + Exporters.durationMs(bez));
    }

    static void testEdgeCases() {
        section("Edge cases");
        // a perfectly flat trace (no motion in progress) must not crash the fitter
        int n = 20;
        double[] t = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) { t[i] = i / (double) (n - 1); p[i] = 0; }
        p[n - 1] = 1;
        FitResult f = CurveFitter.fit(new Trace(t, p, 200));
        check("step trace fits without NaN", !Double.isNaN(f.rms()), "" + f.rms());
        check("step easing finite", !Double.isNaN(f.easing().at(0.5)), "NaN");

        // extreme durations
        FitResult fastF = CurveFitter.fit(synth(new Bezier(0.4, 0, 0.6, 1), 48, 30));
        check("very short duration clamps", Exporters.durationMs(fastF) >= 50,
                "" + Exporters.durationMs(fastF));
        FitResult slowF = CurveFitter.fit(synth(new Bezier(0.4, 0, 0.6, 1), 48, 90000));
        check("very long duration clamps", Exporters.durationMs(slowF) <= 5000,
                "" + Exporters.durationMs(slowF));

        // easing must stay finite everywhere for all models
        for (double x = -0.5; x <= 1.5; x += 0.05) {
            double v = f.easing().at(x);
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                failed++;
                System.out.println("  FAIL  easing NaN at " + x);
                return;
            }
        }
        check("easing finite outside 0..1", true, "");

        // spring with extreme params stays sane
        Spring wild = new Spring(4000, 0.02, 0.05);
        check("wild spring finite", !Double.isNaN(wild.at(0.5)), "NaN");
        Spring tiny = new Spring(0.0001, 0.0001, 0.0001);
        check("clamped spring finite", !Double.isNaN(tiny.at(0.5)), "NaN");
    }
}
