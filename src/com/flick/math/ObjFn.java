package com.flick.math;

/** Scalar objective for the optimizer. */
public interface ObjFn {
    double f(double[] p);
}
