import com.flick.LibraryExport;
import com.flick.SavedCurve;
import com.flick.math.Bezier;
import com.flick.math.CurveFitter;
import com.flick.math.Easing;
import com.flick.math.FitResult;
import com.flick.math.Spring;
import com.flick.math.Trace;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the app-level classes that carry no Android imports: persistence encoding,
 * search matching, and the Kotlin library export.
 */
public class TestApp {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name + "  -- " + detail); }
    }

    static void near(String name, double got, double want, double tol) {
        check(name, Math.abs(got - want) <= tol, "got " + got + " want " + want);
    }

    static void section(String s) { System.out.println("\n== " + s + " =="); }

    static Trace synth(Easing e, int n, double durMs) {
        double[] t = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i / (double) (n - 1);
            p[i] = e.at(t[i]);
        }
        return new Trace(t, p, durMs);
    }

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

    public static void main(String[] args) {
        testEncoding();
        testRoundTrip();
        testSearch();
        testRobustness();
        testKotlinLibrary();

        System.out.println("\n----------------------------------------");
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void testEncoding() {
        section("Encoding");
        double[] v = { 0.0, 0.5, -0.25, 1.75 };
        double[] back = SavedCurve.decode(SavedCurve.encode(v));
        check("length preserved", back.length == v.length, "" + back.length);
        for (int i = 0; i < v.length; i++) near("value " + i, back[i], v[i], 1e-12);

        check("empty encodes", SavedCurve.encode(new double[0]).equals(""), "not empty");
        check("empty decodes", SavedCurve.decode("").length == 0, "not empty");
        check("null decodes", SavedCurve.decode(null).length == 0, "not empty");
        check("garbage decodes to 0", SavedCurve.decode("a,b").length == 2, "wrong length");
        near("garbage value is 0", SavedCurve.decode("a,b")[0], 0, 1e-12);
    }

    static void testRoundTrip() {
        section("SavedCurve round trip");

        FitResult bez = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 420));
        SavedCurve c = SavedCurve.from("Snappy Settle", "ui, snappy", bez);
        check("name kept", c.name.equals("Snappy Settle"), c.name);
        check("tags kept", c.tags.equals("ui, snappy"), c.tags);
        check("model kept", c.model.equals("BEZIER"), c.model);
        near("duration kept", c.durationMs, 420, 1e-9);
        check("32 stops stored", c.traceStops.length == 32, "" + c.traceStops.length);
        check("not favourite by default", !c.favourite, "favourite");

        // simulate a database write and read
        SavedCurve c2 = new SavedCurve();
        c2.name = c.name; c2.tags = c.tags; c2.model = c.model; c2.favourite = c.favourite;
        c2.x1 = c.x1; c2.y1 = c.y1; c2.x2 = c.x2; c2.y2 = c.y2;
        c2.omega = c.omega; c2.zeta = c.zeta;
        c2.durationMs = c.durationMs;
        c2.traceStops = SavedCurve.decode(SavedCurve.encode(c.traceStops));

        FitResult back = c2.toFit();
        check("model restored", back.model == FitResult.Model.BEZIER, "" + back.model);
        near("x1 restored", back.bezier.x1, bez.bezier.x1, 1e-9);
        near("y2 restored", back.bezier.y2, bez.bezier.y2, 1e-9);
        near("duration restored", back.durationMs(), 420, 1e-9);

        double worst = 0;
        for (double t = 0; t <= 1.0; t += 0.01) {
            worst = Math.max(worst, Math.abs(back.easing().at(t) - bez.easing().at(t)));
        }
        check("bezier easing identical after reload", worst < 1e-9, "worst=" + worst);

        // a hand-tuned duration survives the round trip
        FitResult tuned = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 420));
        tuned.durationOverrideMs = 250;
        SavedCurve tc = SavedCurve.from("Tuned", "", tuned);
        near("tuned duration saved", tc.durationMs, 250, 1e-9);
        near("tuned duration reloaded", tc.toFit().durationMs(), 250, 1e-9);

        // spring
        FitResult sp = CurveFitter.fit(synth(new Spring(22, 0.35, 0.5), 48, 500));
        SavedCurve sc = SavedCurve.from("Bouncy", "playful", sp);
        FitResult sback = sc.toFit();
        if (sp.model == FitResult.Model.SPRING) {
            check("spring model restored", sback.model == FitResult.Model.SPRING, "" + sback.model);
            near("omega restored", sback.spring.omega, sp.spring.omega, 1e-9);
            near("zeta restored", sback.spring.zeta, sp.spring.zeta, 1e-9);
            double w2 = 0;
            for (double t = 0; t <= 1.0; t += 0.01) {
                w2 = Math.max(w2, Math.abs(sback.easing().at(t) - sp.easing().at(t)));
            }
            check("spring easing identical after reload", w2 < 1e-9, "worst=" + w2);
        }

        // path keeps its shape through the 32-stop trace
        FitResult po = CurveFitter.fit(wiggle());
        SavedCurve pc = SavedCurve.from("Wiggle", "weird", po);
        FitResult pback = pc.toFit();
        if (po.model == FitResult.Model.PATH) {
            check("path model restored", pback.model == FitResult.Model.PATH, "" + pback.model);
            check("path has segments", pback.path.segments() >= 2, "" + pback.path.segments());
            double w3 = 0;
            for (double x = 0; x <= 1.0; x += 0.01) {
                w3 = Math.max(w3, Math.abs(pback.easing().at(x) - po.easing().at(x)));
            }
            check("path shape preserved", w3 < 0.06, "worst=" + w3);
        }
    }

    static void testSearch() {
        section("Library search matching");
        FitResult bez = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 420));
        SavedCurve c = SavedCurve.from("Snappy Settle", "ui, buttons", bez);

        check("empty query matches", c.matches(""), "no match");
        check("null query matches", c.matches(null), "no match");
        check("whitespace query matches", c.matches("   "), "no match");
        check("name match", c.matches("snappy"), "no match");
        check("name match is case insensitive", c.matches("SNAPPY"), "no match");
        check("partial name match", c.matches("ettl"), "no match");
        check("tag match", c.matches("buttons"), "no match");
        check("model match", c.matches("bezier"), "no match");
        check("query is trimmed", c.matches("  snappy  "), "no match");
        check("non-match rejected", !c.matches("zzzz"), "matched");

        SavedCurve noTags = SavedCurve.from("Plain", "", bez);
        check("empty tags do not crash", !noTags.matches("anything"), "matched");
        check("empty tags still name-matches", noTags.matches("plain"), "no match");
    }

    static void testRobustness() {
        section("Robustness");
        SavedCurve empty = new SavedCurve();
        empty.durationMs = 300;
        FitResult f = empty.toFit();
        check("empty stops does not crash", f != null, "null");
        check("empty stops easing finite", !Double.isNaN(f.easing().at(0.5)), "NaN");

        SavedCurve bad = new SavedCurve();
        bad.model = "NOT_A_MODEL";
        bad.durationMs = 300;
        bad.traceStops = new double[]{ 0, 0.5, 1 };
        check("unknown model falls back to bezier",
                bad.toFit().model == FitResult.Model.BEZIER, "" + bad.toFit().model);

        SavedCurve zeroDur = new SavedCurve();
        zeroDur.durationMs = 0;
        zeroDur.traceStops = new double[]{ 0, 1 };
        check("zero duration survives", !Double.isNaN(zeroDur.toFit().easing().at(0.5)), "NaN");

        SavedCurve one = new SavedCurve();
        one.durationMs = 200;
        one.traceStops = new double[]{ 0.5 };
        check("single stop survives", one.toFit() != null, "null");

        SavedCurve nullTags = new SavedCurve();
        nullTags.durationMs = 200;
        nullTags.traceStops = new double[]{ 0, 1 };
        check("from() tolerates null tags",
                SavedCurve.from("X", null, one.toFit()).tags.equals(""), "not empty");
    }

    static void testKotlinLibrary() {
        section("Kotlin library export");
        String empty = LibraryExport.kotlin(new ArrayList<SavedCurve>());
        check("empty has object", empty.contains("object Motion {"), empty);
        check("empty has note", empty.contains("no curves saved yet"), empty);
        check("empty closes brace", empty.trim().endsWith("}"), empty);
        check("null list safe", LibraryExport.kotlin(null).contains("object Motion"), "");

        FitResult bez = CurveFitter.fit(synth(new Bezier(0.42, 0, 0.58, 1), 48, 420));
        List<SavedCurve> list = new ArrayList<SavedCurve>();
        list.add(SavedCurve.from("Snap", "", bez));
        list.add(SavedCurve.from("Snap", "", bez));
        list.add(SavedCurve.from("slow drift", "", bez));

        String k = LibraryExport.kotlin(list);
        check("has first Snap", k.contains("val Snap "), k);
        check("dedupes to Snap2", k.contains("val Snap2 "), k);
        check("camel cases names", k.contains("val SlowDrift "), k);
        check("imports Compose", k.contains("import androidx.compose.animation.core"), "");

        int lines = 0;
        for (String line : k.split("\n")) if (line.trim().startsWith("val ")) lines++;
        check("one val per curve", lines == 3, "" + lines);

        check("no NaN", !k.contains("NaN") && !k.contains("Infinity"), k);

        FitResult sp = CurveFitter.fit(synth(new Spring(22, 0.35, 0.5), 48, 500));
        if (sp.model == FitResult.Model.SPRING) {
            List<SavedCurve> s = new ArrayList<SavedCurve>();
            s.add(SavedCurve.from("Bouncy", "", sp));
            String ks = LibraryExport.kotlin(s);
            check("spring exports as spring()", ks.contains("spring<Float>("), ks);
            check("spring has dampingRatio", ks.contains("dampingRatio"), ks);
        }

        FitResult po = CurveFitter.fit(wiggle());
        if (po.model == FitResult.Model.PATH) {
            List<SavedCurve> p = new ArrayList<SavedCurve>();
            p.add(SavedCurve.from("Wiggle", "", po));
            String kp = LibraryExport.kotlin(p);
            check("path exports as stops", kp.contains("WiggleStops = floatArrayOf"), kp);
        }
    }
}
