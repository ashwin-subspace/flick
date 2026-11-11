package com.flick.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a fit into ready-to-paste code for every target that has a real notion of
 * easing. Pure functions with no Android dependency, so they are unit-testable.
 */
public final class Exporters {
    private Exporters() {}

    public static final class Item {
        public final String label, lang, code;
        public Item(String label, String lang, String code) {
            this.label = label; this.lang = lang; this.code = code;
        }
    }

    /** Duration rounded to the nearest 10ms, which is how people actually write it. */
    public static int durationMs(FitResult f) {
        int d = (int) Math.round(f.durationMs() / 10.0) * 10;
        return Math.max(50, Math.min(5000, d));
    }

    private static String stopsList(PolyEasing p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.stops.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Fmt.n(p.stops[i]));
        }
        return sb.toString();
    }

    private static String floatList(PolyEasing p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.stops.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Fmt.n(p.stops[i])).append("f");
        }
        return sb.toString();
    }

    /** CSS linear() needs enough stops to hold every segment of a path curve. */
    private static PolyEasing sampled(FitResult f) {
        int n = f.model == FitResult.Model.PATH
                ? Math.max(12, Math.min(24, f.path.segments() * 4)) : 12;
        return PolyEasing.sample(f.easing(), n);
    }

    // ---- individual targets -------------------------------------------------

    public static String css(FitResult f) {
        int d = durationMs(f);
        if (f.model == FitResult.Model.BEZIER) {
            Bezier b = f.bezier;
            return "transition: transform " + d + "ms cubic-bezier("
                    + Fmt.n(b.x1) + ", " + Fmt.n(b.y1) + ", "
                    + Fmt.n(b.x2) + ", " + Fmt.n(b.y2) + ");";
        }
        return "transition: transform " + d + "ms linear(" + stopsList(sampled(f)) + ");";
    }

    public static String cssLinear(FitResult f) {
        return "animation-timing-function: linear(" + stopsList(sampled(f)) + ");";
    }

    public static String compose(FitResult f) {
        int d = durationMs(f);
        switch (f.model) {
            case SPRING: {
                Spring s = f.spring;
                return "animationSpec = spring(\n"
                        + "    dampingRatio = " + Fmt.n(s.zeta) + "f,\n"
                        + "    stiffness = " + Fmt.f(s.stiffness(), 0) + "f\n"
                        + ")";
            }
            case PATH: {
                return "val flickEasing = Easing { t ->\n"
                        + "    val s = floatArrayOf(" + floatList(sampled(f)) + ")\n"
                        + "    val x = t * (s.size - 1)\n"
                        + "    val i = x.toInt().coerceAtMost(s.size - 2)\n"
                        + "    val u = x - i\n"
                        + "    s[i] * (1 - u) + s[i + 1] * u\n"
                        + "}\n"
                        + "animationSpec = tween(" + d + ", easing = flickEasing)";
            }
            default: {
                Bezier b = f.bezier;
                return "animationSpec = tween(\n"
                        + "    durationMillis = " + d + ",\n"
                        + "    easing = CubicBezierEasing("
                        + Fmt.n(b.x1) + "f, " + Fmt.n(b.y1) + "f, "
                        + Fmt.n(b.x2) + "f, " + Fmt.n(b.y2) + "f)\n"
                        + ")";
            }
        }
    }

    public static String androidXml(FitResult f) {
        String head = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<pathInterpolator xmlns:android=\"http://schemas.android.com/apk/res/android\"\n";
        if (f.model == FitResult.Model.BEZIER) {
            Bezier b = f.bezier;
            return head
                    + "    android:controlX1=\"" + Fmt.n(b.x1) + "\"\n"
                    + "    android:controlY1=\"" + Fmt.n(b.y1) + "\"\n"
                    + "    android:controlX2=\"" + Fmt.n(b.x2) + "\"\n"
                    + "    android:controlY2=\"" + Fmt.n(b.y2) + "\" />";
        }
        if (f.model == FitResult.Model.PATH) {
            // real cubic segments, exactly the spline
            return head + "    android:pathData=\"" + f.path.toPathData() + "\" />";
        }
        // spring: sample it into a path, since XML has no spring interpolator
        PolyEasing p = sampled(f);
        StringBuilder d = new StringBuilder("M 0,0");
        for (int i = 1; i < p.stops.length; i++) {
            double x = i / (double) (p.stops.length - 1);
            d.append(" L ").append(Fmt.n(x)).append(",").append(Fmt.n(p.stops[i]));
        }
        return head + "    android:pathData=\"" + d + "\" />";
    }

    public static String flutter(FitResult f) {
        int d = durationMs(f);
        if (f.model == FitResult.Model.SPRING) {
            Spring s = f.spring;
            return "final spring = SpringDescription(\n"
                    + "  mass: 1.0,\n"
                    + "  stiffness: " + Fmt.f(s.stiffness(), 1) + ",\n"
                    + "  damping: " + Fmt.f(2 * s.zeta * s.omega, 2) + ",\n"
                    + ");";
        }
        if (f.model == FitResult.Model.PATH) {
            return "// no closed form; sample the stops\n"
                    + "const stops = <double>[" + stopsList(sampled(f)) + "];";
        }
        Bezier b = f.bezier;
        return "curve: const Cubic(" + Fmt.n(b.x1) + ", " + Fmt.n(b.y1) + ", "
                + Fmt.n(b.x2) + ", " + Fmt.n(b.y2) + "),\n"
                + "duration: const Duration(milliseconds: " + d + "),";
    }

    public static String swiftUi(FitResult f) {
        double d = durationMs(f) / 1000.0;
        if (f.model == FitResult.Model.SPRING) {
            Spring s = f.spring;
            return ".animation(.interpolatingSpring(\n"
                    + "    stiffness: " + Fmt.f(s.stiffness(), 1) + ",\n"
                    + "    damping: " + Fmt.f(2 * s.zeta * s.omega, 2) + "))";
        }
        Bezier b = f.model == FitResult.Model.BEZIER
                ? f.bezier : new Bezier(0.33, 0, 0.67, 1);
        String note = f.model == FitResult.Model.PATH
                ? "// approximated; SwiftUI has no linear() equivalent\n" : "";
        return note + ".animation(.timingCurve("
                + Fmt.n(b.x1) + ", " + Fmt.n(b.y1) + ", "
                + Fmt.n(b.x2) + ", " + Fmt.n(b.y2)
                + ", duration: " + Fmt.f(d, 2) + "))";
    }

    /**
     * After Effects keyframe easing. AE does not use control points — it uses
     * influence, the percentage of the time span each handle occupies. For a cubic
     * bezier that is x1 on the outgoing key and 1-x2 on the incoming one.
     */
    public static String afterEffects(FitResult f) {
        Bezier b = f.model == FitResult.Model.BEZIER
                ? f.bezier : new Bezier(0.33, 0, 0.67, 1);
        double outInf = b.x1 * 100.0;
        double inInf = (1.0 - b.x2) * 100.0;
        double outSpeedPct = b.x1 > 1e-6 ? (b.y1 / b.x1) * 100.0 : 0;
        double inSpeedPct = (1 - b.x2) > 1e-6 ? ((1 - b.y2) / (1 - b.x2)) * 100.0 : 0;
        String note = f.model == FitResult.Model.BEZIER
                ? "" : "// approximated from a non-bezier fit\n";
        return note
                + "Keyframe 1  Easy Ease Out\n"
                + "  influence " + Fmt.f(outInf, 1) + "%   speed " + Fmt.f(outSpeedPct, 1) + "%\n"
                + "Keyframe 2  Easy Ease In\n"
                + "  influence " + Fmt.f(inInf, 1) + "%   speed " + Fmt.f(inSpeedPct, 1) + "%\n"
                + "Span " + durationMs(f) + "ms";
    }

    /** One named constant for the exported Kotlin library file. */
    public static String kotlinConstant(String name, FitResult f) {
        String id = identifier(name);
        if (f.model == FitResult.Model.SPRING) {
            Spring s = f.spring;
            return "    val " + id + " = spring<Float>(dampingRatio = " + Fmt.n(s.zeta)
                    + "f, stiffness = " + Fmt.f(s.stiffness(), 0) + "f)";
        }
        if (f.model == FitResult.Model.PATH) {
            return "    val " + id + "Stops = floatArrayOf(" + floatList(sampled(f)) + ")";
        }
        Bezier b = f.bezier;
        return "    val " + id + " = CubicBezierEasing(" + Fmt.n(b.x1) + "f, " + Fmt.n(b.y1)
                + "f, " + Fmt.n(b.x2) + "f, " + Fmt.n(b.y2) + "f)   // "
                + durationMs(f) + "ms";
    }

    /** Turn a free-text curve name into a valid Kotlin identifier. */
    public static String identifier(String name) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            } else {
                up = true;
            }
        }
        if (sb.length() == 0) return "Curve";
        if (Character.isDigit(sb.charAt(0))) sb.insert(0, "C");
        return sb.toString();
    }

    public static List<Item> all(FitResult f) {
        List<Item> out = new ArrayList<Item>();
        out.add(new Item("CSS", "css", css(f)));
        out.add(new Item("CSS linear()", "css", cssLinear(f)));
        out.add(new Item("Compose", "kotlin", compose(f)));
        out.add(new Item("Android XML", "xml", androidXml(f)));
        out.add(new Item("Flutter", "dart", flutter(f)));
        out.add(new Item("SwiftUI", "swift", swiftUi(f)));
        out.add(new Item("After Effects", "text", afterEffects(f)));
        return out;
    }
}
