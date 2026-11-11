package com.flick.math;

/** All three candidate models for one trace, plus which one was chosen and why. */
public final class FitResult {
    public enum Model { BEZIER, SPRING, PATH }

    public final Bezier bezier;
    public final Spring spring;
    public final SplineEasing path;
    public final double rmsBezier, rmsSpring, rmsPath;
    public final Trace trace;
    public Model model;
    public String reason;

    /** Duration override in ms, applied after hand-tuning. 0 means use the trace's own. */
    public double durationOverrideMs = 0;

    public FitResult(Trace trace, Bezier b, double rb, Spring s, double rs,
                     SplineEasing p, double rp, Model chosen, String reason) {
        this.trace = trace;
        this.bezier = b; this.rmsBezier = rb;
        this.spring = s; this.rmsSpring = rs;
        this.path = p;   this.rmsPath = rp;
        this.model = chosen;
        this.reason = reason;
    }

    public double durationMs() {
        return durationOverrideMs > 0 ? durationOverrideMs : trace.durationMs;
    }

    public Easing easing() {
        switch (model) {
            case SPRING: return spring;
            case PATH:   return path;
            default:     return bezier;
        }
    }

    public double rms() {
        switch (model) {
            case SPRING: return rmsSpring;
            case PATH:   return rmsPath;
            default:     return rmsBezier;
        }
    }

    public int paramCount() {
        switch (model) {
            case SPRING: return 2;
            case PATH:   return path.knotCount() * 2;
            default:     return 4;
        }
    }

    public String headline() {
        switch (model) {
            case SPRING: return "This is a spring";
            case PATH:   return path.beats() > 1
                    ? "This is a " + path.beats() + "-beat path"
                    : "This is a shaped path";
            default:     return "This is a bezier";
        }
    }

    /**
     * How a given model describes this trace. Used when the reader switches models by
     * hand, so the explanation never describes a model that is no longer showing.
     */
    public String describe(Model m) {
        switch (m) {
            case SPRING:
                return "Spring at stiffness " + Fmt.f(spring.stiffness(), 0)
                        + ", damping " + Fmt.n(spring.zeta)
                        + " — " + Fmt.f(rmsSpring * 100, 1) + "% error.";
            case PATH:
                return path.segments() + " cubic segments through " + path.knotCount()
                        + " knots — " + Fmt.f(rmsPath * 100, 1) + "% error.";
            default:
                return "A single cubic bezier — " + Fmt.f(rmsBezier * 100, 1) + "% error.";
        }
    }

    public int beats() { return path.beats(); }

    /** Accuracy of the chosen model as a percentage, where 0.25 rms counts as hopeless. */
    public double accuracyPct() { return Math.max(0, 100.0 * (1.0 - rms() / 0.25)); }

    /** A copy with a different bezier, used by the curve editor. */
    public FitResult withBezier(Bezier nb, String newReason) {
        FitResult f = new FitResult(trace, nb, rmsBezier, spring, rmsSpring,
                path, rmsPath, Model.BEZIER, newReason);
        f.durationOverrideMs = durationOverrideMs;
        return f;
    }
}
