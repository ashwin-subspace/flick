package com.flick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.flick.math.Trace;
import com.flick.math.TraceBuilder;

import java.util.List;

/**
 * The capture canvas. Reads every touch sample the digitizer produced, not just the
 * one delivered with each event: Android batches touch input, so getHistorical* is
 * where most of the resolution lives. Without it a fast flick is sampled at ~60Hz
 * instead of the panel's 120-240Hz, and the character of the motion is lost.
 */
public class CaptureView extends View {

    public interface Listener {
        void onStarted();
        void onCaptured(Trace t);
        void onRejected(String reason);
    }

    private final TraceBuilder builder = new TraceBuilder();
    private Listener listener;
    private boolean capturing;
    private float density = 2f;

    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent = Color.parseColor("#4FE3C1");

    public CaptureView(Context c) { super(c); init(); }
    public CaptureView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
        trail.setColor(accent);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(accent);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1f);
        grid.setColor(Color.parseColor("#1C2229"));
        setClickable(true);
    }

    public void setListener(Listener l) { this.listener = l; }

    public void clear() {
        builder.reset();
        capturing = false;
        invalidate();
    }

    private double sec(long eventTimeMs) { return eventTimeMs / 1000.0; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                builder.reset();
                capturing = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                builder.add(e.getX(), e.getY(), sec(e.getEventTime()));
                if (listener != null) listener.onStarted();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!capturing) return true;
                int h = e.getHistorySize();
                for (int i = 0; i < h; i++) {
                    builder.add(e.getHistoricalX(i), e.getHistoricalY(i),
                            sec(e.getHistoricalEventTime(i)));
                }
                builder.add(e.getX(), e.getY(), sec(e.getEventTime()));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!capturing) return true;
                capturing = false;
                builder.add(e.getX(), e.getY(), sec(e.getEventTime()));
                invalidate();
                if (listener != null) {
                    String rej = builder.rejectReason();
                    if (rej != null) {
                        listener.onRejected(rej);
                    } else {
                        Trace t = builder.build();
                        if (t == null) listener.onRejected("Could not read that flick");
                        else listener.onCaptured(t);
                    }
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();

        // faint grid so the surface reads as an instrument, not an empty screen
        float step = 48 * density;
        for (float x = step; x < w; x += step) c.drawLine(x, 0, x, h, grid);
        for (float y = step; y < h; y += step) c.drawLine(0, y, w, y, grid);

        List<double[]> pts = builder.points();
        if (pts.size() < 2) return;

        // Stroke width follows speed, so the drawn trail shows the timing, not just the path.
        double maxSpeed = 1e-9;
        double[] speeds = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            double dt = Math.max(1e-4, b[2] - a[2]);
            speeds[i] = Math.hypot(b[0] - a[0], b[1] - a[1]) / dt;
            if (speeds[i] > maxSpeed) maxSpeed = speeds[i];
        }

        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            float norm = (float) Math.pow(speeds[i] / maxSpeed, 0.6);
            trail.setStrokeWidth((1.5f + norm * 9f) * density);
            int alpha = (int) (90 + 165 * norm);
            trail.setAlpha(Math.max(60, Math.min(255, alpha)));
            c.drawLine((float) a[0], (float) a[1], (float) b[0], (float) b[1], trail);
        }

        double[] start = pts.get(0);
        dot.setAlpha(140);
        c.drawCircle((float) start[0], (float) start[1], 5 * density, dot);

        if (capturing) {
            double[] last = pts.get(pts.size() - 1);
            dot.setAlpha(255);
            c.drawCircle((float) last[0], (float) last[1], 8 * density, dot);
        }
    }
}
