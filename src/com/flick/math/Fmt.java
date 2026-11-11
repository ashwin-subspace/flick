package com.flick.math;

import java.util.Locale;

/** Number formatting shared by the exporters. */
public final class Fmt {
    private Fmt() {}

    /** Trim to 3 decimals, no trailing zeros, no locale commas. */
    public static String n(double v) {
        if (Math.abs(v) < 5e-4) return "0";
        String s = String.format(Locale.US, "%.3f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Fixed decimals, locale-safe. */
    public static String f(double v, int dp) {
        return String.format(Locale.US, "%." + dp + "f", v);
    }

    public static String pct(double v) {
        return String.format(Locale.US, "%.0f%%", v * 100.0);
    }
}
