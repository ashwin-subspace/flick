package com.flick;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.flick.math.Easing;

/**
 * Plays an easing curve as vibration: amplitude follows velocity, so a dash buzzes
 * hard, a hesitation goes quiet, and an overshoot lands as a distinct double pulse.
 * You can feel the shape of a motion with the screen off.
 */
public final class Haptics {
    private Haptics() {}

    public static boolean available(Context ctx) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        return v != null && v.hasVibrator();
    }

    public static boolean play(Context ctx, Easing e, int durationMs) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return false;

        int steps = Math.max(10, Math.min(70, durationMs / 12));
        long stepMs = Math.max(4, durationMs / steps);

        double[] vel = new double[steps];
        double prev = e.at(0);
        double maxV = 1e-9;
        for (int i = 0; i < steps; i++) {
            double t = (i + 1) / (double) steps;
            double cur = e.at(t);
            vel[i] = Math.abs(cur - prev);
            prev = cur;
            if (vel[i] > maxV) maxV = vel[i];
        }

        long[] timings = new long[steps];
        int[] amps = new int[steps];
        for (int i = 0; i < steps; i++) {
            timings[i] = stepMs;
            // gamma below 1 lifts the quiet parts so hesitation is still perceptible
            double norm = Math.pow(vel[i] / maxV, 0.7);
            int a = (int) Math.round(255 * norm);
            amps[i] = Math.max(0, Math.min(255, a));
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
            } else {
                // No amplitude control: approximate with on/off, on where the curve is fast.
                long[] pattern = new long[steps * 2];
                for (int i = 0; i < steps; i++) {
                    boolean on = amps[i] > 96;
                    pattern[i * 2] = on ? 0 : stepMs;
                    pattern[i * 2 + 1] = on ? stepMs : 0;
                }
                v.vibrate(pattern, -1);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void stop(Context ctx) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            try { v.cancel(); } catch (Exception ignored) {}
        }
    }
}
