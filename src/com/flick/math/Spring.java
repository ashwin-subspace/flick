package com.flick.math;

/**
 * Unit step response of a damped harmonic oscillator, normalized so it starts at 0
 * and settles at 1. Handles under-, critically- and over-damped cases.
 *
 * omega is the undamped natural frequency in rad/s, zeta the damping ratio.
 * durationSec maps normalized time in [0,1] onto real seconds.
 */
public final class Spring implements Easing {
    public final double omega, zeta, durationSec;

    public Spring(double omega, double zeta, double durationSec) {
        this.omega = Math.max(0.1, omega);
        this.zeta = Math.max(0.02, zeta);
        this.durationSec = Math.max(0.01, durationSec);
    }

    /** Response at real time t (seconds). */
    public double response(double t) {
        if (t <= 0) return 0;
        double w = omega, z = zeta;
        if (z < 1.0 - 1e-6) {
            double wd = w * Math.sqrt(1 - z * z);
            return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + (z * w / wd) * Math.sin(wd * t));
        } else if (z <= 1.0 + 1e-6) {
            return 1 - Math.exp(-w * t) * (1 + w * t);
        } else {
            double s = Math.sqrt(z * z - 1);
            double r1 = -w * (z - s), r2 = -w * (z + s);
            return 1 - (r2 * Math.exp(r1 * t) - r1 * Math.exp(r2 * t)) / (r2 - r1);
        }
    }

    @Override public double at(double t) {
        if (t <= 0) return 0;
        return response(t * durationSec);
    }

    /** Compose / SpringForce stiffness, taking mass as 1 (stiffness = omega^2). */
    public double stiffness() { return omega * omega; }

    /** Time in seconds until the response stays inside +/- tol of 1. */
    public double settleTime(double tol) {
        double dt = 0.002;
        double last = 0;
        for (double t = 0; t < 12.0; t += dt) {
            if (Math.abs(response(t) - 1) > tol) last = t;
        }
        return last + dt;
    }

    /** Peak overshoot above 1, or 0 when the spring never overshoots. */
    public double overshoot() {
        if (zeta >= 1) return 0;
        double peak = 0;
        double dt = 0.002;
        for (double t = 0; t < 12.0; t += dt) {
            peak = Math.max(peak, response(t));
        }
        return Math.max(0, peak - 1);
    }
}
