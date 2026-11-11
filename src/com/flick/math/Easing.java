package com.flick.math;

/** A normalized easing function: progress at normalized time t in [0,1]. */
public interface Easing {
    double at(double t);
}
