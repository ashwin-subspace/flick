package com.flick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * A small rendering of one curve's shape, for the library grid. A wall of these is
 * how a personal motion vocabulary becomes something you can actually browse.
 */
public class ThumbView extends View {

    private double[] stops = { 0, 1 };
    private int accent = Color.parseColor("#4FE3C1");
    private float density = 2f;

    private final Paint curve = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public ThumbView(Context c) { super(c); init(); }
    public ThumbView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        curve.setStyle(Paint.Style.STROKE);
        curve.setStrokeWidth(2.2f * density);
        curve.setStrokeCap(Paint.Cap.ROUND);
        curve.setColor(accent);
        base.setColor(Color.parseColor("#222A32"));
        base.setStrokeWidth(1f);
    }

    public void setCurve(double[] s, int accentColor) {
        if (s != null && s.length >= 2) this.stops = s;
        this.accent = accentColor;
        curve.setColor(accent);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float pad = 9 * density;

        double lo = 0, hi = 1;
        for (double v : stops) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double range = Math.max(0.35, (hi - lo) * 1.16);
        double mid = (hi + lo) / 2.0;
        double vMin = mid - range / 2, vMax = mid + range / 2;

        float y0 = mapY(0, vMin, vMax, pad, h);
        float y1 = mapY(1, vMin, vMax, pad, h);
        c.drawLine(pad, y0, w - pad, y0, base);
        c.drawLine(pad, y1, w - pad, y1, base);

        path.reset();
        for (int i = 0; i < stops.length; i++) {
            float x = pad + (w - 2 * pad) * i / (float) (stops.length - 1);
            float y = mapY(stops[i], vMin, vMax, pad, h);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        c.drawPath(path, curve);
    }

    private float mapY(double v, double vMin, double vMax, float pad, int h) {
        double f = (v - vMin) / (vMax - vMin);
        return (float) (h - pad - f * (h - 2 * pad));
    }
}
