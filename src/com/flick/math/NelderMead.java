package com.flick.math;

import java.util.Arrays;
import java.util.Comparator;

/** Derivative-free simplex minimizer. No dependencies, adequate for 2-4 parameters. */
public final class NelderMead {
    private NelderMead() {}

    public static double[] minimize(ObjFn fn, double[] start, double step, int maxIter) {
        int n = start.length;
        double[][] simplex = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double[] p = start.clone();
            if (i > 0) p[i - 1] += step;
            simplex[i] = p;
        }
        double[] fv = new double[n + 1];
        for (int i = 0; i <= n; i++) fv[i] = fn.f(simplex[i]);

        final double ALPHA = 1.0, GAMMA = 2.0, RHO = 0.5, SIGMA = 0.5;

        for (int iter = 0; iter < maxIter; iter++) {
            Integer[] idx = new Integer[n + 1];
            for (int i = 0; i <= n; i++) idx[i] = i;
            final double[] fvf = fv;
            Arrays.sort(idx, new Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    return Double.compare(fvf[a], fvf[b]);
                }
            });
            double[][] ns = new double[n + 1][];
            double[] nf = new double[n + 1];
            for (int i = 0; i <= n; i++) { ns[i] = simplex[idx[i]]; nf[i] = fv[idx[i]]; }
            simplex = ns; fv = nf;

            if (Math.abs(fv[n] - fv[0]) < 1e-12) break;

            double[] cen = new double[n];
            for (int i = 0; i < n; i++) {
                double s = 0;
                for (int j = 0; j < n; j++) s += simplex[j][i];
                cen[i] = s / n;
            }

            double[] xr = new double[n];
            for (int i = 0; i < n; i++) xr[i] = cen[i] + ALPHA * (cen[i] - simplex[n][i]);
            double fr = fn.f(xr);

            if (fr < fv[0]) {
                double[] xe = new double[n];
                for (int i = 0; i < n; i++) xe[i] = cen[i] + GAMMA * (xr[i] - cen[i]);
                double fe = fn.f(xe);
                if (fe < fr) { simplex[n] = xe; fv[n] = fe; } else { simplex[n] = xr; fv[n] = fr; }
            } else if (fr < fv[n - 1]) {
                simplex[n] = xr; fv[n] = fr;
            } else {
                double[] xc = new double[n];
                for (int i = 0; i < n; i++) xc[i] = cen[i] + RHO * (simplex[n][i] - cen[i]);
                double fc = fn.f(xc);
                if (fc < fv[n]) { simplex[n] = xc; fv[n] = fc; }
                else {
                    for (int k = 1; k <= n; k++) {
                        for (int i = 0; i < n; i++)
                            simplex[k][i] = simplex[0][i] + SIGMA * (simplex[k][i] - simplex[0][i]);
                        fv[k] = fn.f(simplex[k]);
                    }
                }
            }
        }
        int best = 0;
        for (int i = 1; i <= n; i++) if (fv[i] < fv[best]) best = i;
        return simplex[best];
    }
}
