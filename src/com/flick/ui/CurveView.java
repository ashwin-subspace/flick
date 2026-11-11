package com.flick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.flick.math.Bezier;
import com.flick.math.Easing;
import com.flick.math.FitResult;
import com.flick.math.Trace;

/**
 * The curve plot. Shows what you actually drew (faint) under what was fitted (solid),
 * so the quality of the fit is visible rather than asserted. When the model is a
 * bezier the two control points become draggable, so the flick is a starting point
 * you can refine rather than a take-it-or-leave-it answer.
 */
public class CurveView extends View {

    public interface Listener { void onEdited(Bezier b); }

    private FitResult fit;
    private Listener listener;
    private float density = 2f;
    private double marker = -1;

    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rawP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curveP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();

    private int dragging = -1;   // -1 none, 0 first control point, 1 second
    private double vMin = -0.15, vMax = 1.15;

    public CurveView(Context c) { super(c); init(); }
    public CurveView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        axis.setColor(Color.parseColor("#2A323B"));
        axis.setStrokeWidth(1.5f * density);
        gridP.setColor(Color.parseColor("#191F26"));
        gridP.setStrokeWidth(1f);

        rawP.setStyle(Paint.Style.STROKE);
        rawP.setStrokeWidth(2.5f * density);
        rawP.setColor(Color.parseColor("#55FFFFFF"));
        rawP.setStrokeCap(Paint.Cap.ROUND);

        curveP.setStyle(Paint.Style.STROKE);
        curveP.setStrokeWidth(3.5f * density);
        curveP.setColor(Color.parseColor("#4FE3C1"));
        curveP.setStrokeCap(Paint.Cap.ROUND);

        handleLine.setStyle(Paint.Style.STROKE);
        handleLine.setStrokeWidth(1.5f * density);
        handleLine.setColor(Color.parseColor("#886B7684"));

        handleFill.setStyle(Paint.Style.FILL);
        handleFill.setColor(Color.parseColor("#FFE8EDF2"));

        markerP.setStyle(Paint.Style.FILL);
        markerP.setColor(Color.parseColor("#FFFF9F6B"));

        label.setColor(Color.parseColor("#FF5C6672"));
        label.setTextSize(10 * density);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setFit(FitResult f) {
        this.fit = f;
        // While a handle is down, the plot scale has to stay put. Recomputing it
        // from the handle's own position makes the next finger sample land on a
        // different value, so the handle runs away from the thumb.
        if (dragging < 0) recomputeRange();
        invalidate();
    }

    /** Progress dot position, or a negative value to hide it. */
    public void setMarker(double t) { this.marker = t; invalidate(); }

    private void recomputeRange() {
        double lo = 0, hi = 1;
        if (fit != null) {
            for (int i = 0; i < fit.trace.p.length; i++) {
                lo = Math.min(lo, fit.trace.p[i]);
                hi = Math.max(hi, fit.trace.p[i]);
            }
            Easing e = fit.easing();
            for (double t = 0; t <= 1.0001; t += 0.02) {
                double v = e.at(t);
                if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            }
            // Control points can sit far outside the curve they produce. Include them,
            // or a handle ends up off-plot and cannot be grabbed.
            if (fit.model == FitResult.Model.BEZIER) {
                lo = Math.min(lo, Math.min(fit.bezier.y1, fit.bezier.y2));
                hi = Math.max(hi, Math.max(fit.bezier.y1, fit.bezier.y2));
            }
        }
        double pad = Math.max(0.12, (hi - lo) * 0.10);
        vMin = lo - pad;
        vMax = hi + pad;
        if (vMax - vMin < 0.4) { vMax = vMin + 0.4; }
    }

    private float padL() { return 26 * density; }
    private float padR() { return 16 * density; }
    private float padT() { return 14 * density; }
    private float padB() { return 18 * density; }

    private float sx(double t) {
        float l = padL(), r = getWidth() - padR();
        return (float) (l + t * (r - l));
    }

    private float sy(double v) {
        float top = padT(), bot = getHeight() - padB();
        double f = (v - vMin) / (vMax - vMin);
        return (float) (bot - f * (bot - top));
    }

    private double invX(float px) {
        float l = padL(), r = getWidth() - padR();
        return (px - l) / (double) (r - l);
    }

    private double invY(float py) {
        float top = padT(), bot = getHeight() - padB();
        return vMin + (bot - py) / (double) (bot - top) * (vMax - vMin);
    }

    private boolean editable() {
        return fit != null && fit.model == FitResult.Model.BEZIER;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!editable()) return false;
        float x = e.getX(), y = e.getY();
        Bezier b = fit.bezier;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float d1 = (float) Math.hypot(x - sx(b.x1), y - sy(b.y1));
                float d2 = (float) Math.hypot(x - sx(b.x2), y - sy(b.y2));
                float thresh = 34 * density;
                if (d1 <= thresh && d1 <= d2) dragging = 0;
                else if (d2 <= thresh) dragging = 1;
                else return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragging < 0) return false;
                double nx = Math.max(0, Math.min(1, invX(x)));
                double ny = Math.max(-3, Math.min(4, invY(y)));
                Bezier nb = dragging == 0
                        ? new Bezier(nx, ny, b.x2, b.y2)
                        : new Bezier(b.x1, b.y1, nx, ny);
                applyEdit(nb);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging < 0) return false;
                dragging = -1;
                recomputeRange();
                invalidate();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
        }
        return false;
    }

    private void applyEdit(Bezier nb) {
        this.fit = fit.withBezier(nb, fit.reason);
        invalidate();
        if (listener != null) listener.onEdited(nb);
    }

    @Override protected void onDraw(Canvas c) {
        float l = padL(), r = getWidth() - padR();
        float top = padT(), bot = getHeight() - padB();
        if (r <= l || bot <= top) return;

        for (int i = 1; i < 4; i++) {
            float gx = l + (r - l) * i / 4f;
            c.drawLine(gx, top, gx, bot, gridP);
        }

        float y0 = sy(0), y1 = sy(1);
        c.drawLine(l, y0, r, y0, axis);
        c.drawLine(l, y1, r, y1, axis);
        c.drawText("1", 4 * density, y1 + 4 * density, label);
        c.drawText("0", 4 * density, y0 + 4 * density, label);

        if (fit == null) return;

        Trace tr = fit.trace;
        path.reset();
        for (int i = 0; i < tr.t.length; i++) {
            float px = sx(tr.t[i]), py = sy(tr.p[i]);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        c.drawPath(path, rawP);

        Easing e = fit.easing();
        path.reset();
        boolean first = true;
        for (double t = 0; t <= 1.0001; t += 0.008) {
            double v = e.at(Math.min(1, t));
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            float px = sx(Math.min(1, t)), py = sy(v);
            if (first) { path.moveTo(px, py); first = false; } else { path.lineTo(px, py); }
        }
        c.drawPath(path, curveP);

        if (editable()) {
            Bezier b = fit.bezier;
            c.drawLine(sx(0), sy(0), sx(b.x1), sy(b.y1), handleLine);
            c.drawLine(sx(1), sy(1), sx(b.x2), sy(b.y2), handleLine);
            drawHandle(c, sx(b.x1), sy(b.y1));
            drawHandle(c, sx(b.x2), sy(b.y2));
        }

        if (marker >= 0 && marker <= 1) {
            double v = e.at(marker);
            if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                c.drawCircle(sx(marker), sy(v), 5.5f * density, markerP);
            }
        }
    }

    private void drawHandle(Canvas c, float x, float y) {
        handleFill.setColor(Color.parseColor("#FF0B0D10"));
        c.drawCircle(x, y, 8.5f * density, handleFill);
        handleFill.setColor(Color.parseColor("#FFE8EDF2"));
        c.drawCircle(x, y, 5.5f * density, handleFill);
    }
}
