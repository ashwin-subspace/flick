package com.flick.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.flick.math.Bezier;
import com.flick.math.Easing;

/**
 * Five demos driven by the same curve at once.
 *
 * One moving box is misleading: a curve that feels right on translation can feel
 * sluggish on opacity and wrong on scale. Each lane also carries a faint ghost running
 * Material's FastOutSlowIn in sync, so the difference from the default is visible
 * rather than remembered.
 */
public class PreviewWall extends View {

    public interface ProgressListener { void onProgress(double t); }

    private static final Easing GHOST = new Bezier(0.4, 0.0, 0.2, 1.0);
    private static final int LANES = 5;
    private static final long HOLD_MS = 420;

    private Easing easing = new Bezier(0.42, 0, 0.58, 1);
    private int durationMs = 400;
    private boolean ghost = true;
    private double speed = 1.0;
    private boolean playing = false;
    private double progress = 0;
    private long cycleStart = 0;
    private ProgressListener progressListener;

    private float density = 2f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private static final String[] NAMES = { "move", "scale", "fade", "rotate", "stagger" };

    public PreviewWall(Context c) { super(c); init(); }
    public PreviewWall(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        fill.setColor(Color.parseColor("#4FE3C1"));
        ghostPaint.setColor(Color.parseColor("#FFFF9F6B"));
        ghostPaint.setAlpha(60);
        lane.setColor(Color.parseColor("#14181D"));
        label.setColor(Color.parseColor("#FF5C6672"));
        label.setTextSize(9.5f * density);
    }

    public void setEasing(Easing e, int durationMs) {
        this.easing = e;
        this.durationMs = Math.max(60, durationMs);
        invalidate();
    }

    public void setGhost(boolean g) { ghost = g; invalidate(); }
    public boolean ghost() { return ghost; }

    public void setSpeed(double s) { this.speed = Math.max(0.1, s); }
    public double speed() { return speed; }

    public void setProgressListener(ProgressListener l) { progressListener = l; }

    public boolean isPlaying() { return playing; }

    public void play() {
        playing = true;
        cycleStart = System.nanoTime() - (long) (progress * durationMs / speed * 1_000_000L);
        postInvalidateOnAnimation();
    }

    public void pause() { playing = false; invalidate(); }

    public void toggle() { if (playing) pause(); else play(); }

    /** Scrub to a position. Stops playback so the two do not fight. */
    public void setProgress(double t) {
        playing = false;
        progress = Math.max(0, Math.min(1, t));
        invalidate();
    }

    public double progress() { return progress; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (LANES * 52 * density);
        setMeasuredDimension(w, h);
    }

    private double safe(Easing e, double t) {
        double v = e.at(t);
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.max(-2, Math.min(3, v));
    }

    @Override protected void onDraw(Canvas c) {
        if (playing) {
            long now = System.nanoTime();
            double cycleMs = durationMs / speed + HOLD_MS;
            double elapsed = (now - cycleStart) / 1_000_000.0;
            if (elapsed >= cycleMs) {
                cycleStart = now;
                elapsed = 0;
            }
            progress = Math.min(1.0, elapsed / (durationMs / speed));
            if (progressListener != null) progressListener.onProgress(progress);
        }

        float laneH = getHeight() / (float) LANES;
        float pad = 12 * density;
        float w = getWidth();

        for (int i = 0; i < LANES; i++) {
            float top = i * laneH;
            rect.set(0, top + 2 * density, w, top + laneH - 2 * density);
            c.drawRoundRect(rect, 8 * density, 8 * density, lane);
            c.drawText(NAMES[i], pad, top + laneH - 7 * density, label);

            float cy = top + laneH / 2f;
            float left = pad + 44 * density;
            float right = w - pad;

            double v = safe(easing, progress);
            double g = safe(GHOST, progress);

            switch (i) {
                case 0: drawMove(c, left, right, cy, v, g); break;
                case 1: drawScale(c, left, right, cy, v, g); break;
                case 2: drawFade(c, left, right, cy, v, g); break;
                case 3: drawRotate(c, left, right, cy, v, g); break;
                case 4: drawStagger(c, left, right, cy, laneH); break;
            }
        }

        if (playing) postInvalidateOnAnimation();
    }

    private void drawMove(Canvas c, float l, float r, float cy, double v, double g) {
        float size = 18 * density;
        float span = (r - l) - size;
        if (ghost) {
            rect.set(l + (float) (g * span), cy - size / 2, l + (float) (g * span) + size, cy + size / 2);
            c.drawRoundRect(rect, 4 * density, 4 * density, ghostPaint);
        }
        rect.set(l + (float) (v * span), cy - size / 2, l + (float) (v * span) + size, cy + size / 2);
        fill.setAlpha(255);
        c.drawRoundRect(rect, 4 * density, 4 * density, fill);
    }

    private void drawScale(Canvas c, float l, float r, float cy, double v, double g) {
        float max = 15 * density;
        float cx = (l + r) / 2f;
        if (ghost) c.drawCircle(cx, cy, (float) Math.max(0, g) * max, ghostPaint);
        fill.setAlpha(255);
        c.drawCircle(cx, cy, (float) Math.max(0, v) * max, fill);
    }

    private void drawFade(Canvas c, float l, float r, float cy, double v, double g) {
        float wBox = 56 * density, hBox = 20 * density;
        float rise = 12 * density;
        float cx = (l + r) / 2f;
        if (ghost) {
            float gy = cy + (float) ((1 - g) * rise);
            rect.set(cx - wBox / 2, gy - hBox / 2, cx + wBox / 2, gy + hBox / 2);
            ghostPaint.setAlpha((int) (60 * Math.max(0, Math.min(1, g))));
            c.drawRoundRect(rect, 5 * density, 5 * density, ghostPaint);
            ghostPaint.setAlpha(60);
        }
        float y = cy + (float) ((1 - v) * rise);
        rect.set(cx - wBox / 2, y - hBox / 2, cx + wBox / 2, y + hBox / 2);
        fill.setAlpha((int) (255 * Math.max(0, Math.min(1, v))));
        c.drawRoundRect(rect, 5 * density, 5 * density, fill);
        fill.setAlpha(255);
    }

    private void drawRotate(Canvas c, float l, float r, float cy, double v, double g) {
        float size = 17 * density;
        float cx = (l + r) / 2f;
        if (ghost) {
            c.save();
            c.rotate((float) (g * 180), cx, cy);
            rect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            c.drawRoundRect(rect, 3 * density, 3 * density, ghostPaint);
            c.restore();
        }
        c.save();
        c.rotate((float) (v * 180), cx, cy);
        rect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        fill.setAlpha(255);
        c.drawRoundRect(rect, 3 * density, 3 * density, fill);
        c.restore();
    }

    private void drawStagger(Canvas c, float l, float r, float cy, float laneH) {
        int n = 5;
        float barH = 4 * density;
        float gap = 3 * density;
        float totalH = n * barH + (n - 1) * gap;
        float y = cy - totalH / 2;
        float maxW = (r - l);
        for (int i = 0; i < n; i++) {
            double shifted = Math.max(0, Math.min(1, (progress - i * 0.07) / 0.93));
            double v = safe(easing, shifted);
            float wBar = (float) Math.max(0, v) * maxW;
            rect.set(l, y, l + wBar, y + barH);
            fill.setAlpha(255 - i * 22);
            c.drawRoundRect(rect, barH / 2, barH / 2, fill);
            y += barH + gap;
        }
        fill.setAlpha(255);
    }
}
