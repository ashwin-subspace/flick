package com.flick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.flick.math.Bezier;
import com.flick.math.CurveFitter;
import com.flick.math.Exporters;
import com.flick.math.FitResult;
import com.flick.math.Fmt;
import com.flick.math.Trace;
import com.flick.ui.CaptureView;
import com.flick.ui.CurveView;
import com.flick.ui.PreviewWall;
import com.flick.ui.ThumbView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements CaptureView.Listener {

    private static final int C_BEZIER = Color.parseColor("#4FE3C1");
    private static final int C_SPRING = Color.parseColor("#FF9F6B");
    private static final int C_PATH   = Color.parseColor("#9B8CFF");
    private static final int C_TEXT   = Color.parseColor("#FFE8EDF2");

    private View pageCapture, pageResult, pageLibrary;
    private CaptureView capture;
    private View captureOverlay;
    private TextView captureErr;
    private CurveView curveView;
    private PreviewWall wall;
    private SeekBar scrub;
    private TextView headline, reason, stats, curveHint, durLabel;
    private TextView chipBezier, chipSpring, chipPath;
    private TextView btnSnap, btnMirror, btnClamp;
    private TextView btnPlay, btnSpeed, btnGhost, btnHaptic;
    private LinearLayout exportsBox;
    private GridView grid;
    private TextView libEmpty;
    private EditText libSearch;

    private FitResult fit;
    private CurveStore store;
    private final List<SavedCurve> curves = new ArrayList<SavedCurve>();
    private LibraryAdapter adapter;

    private final double[] SPEEDS = { 1.0, 0.5, 0.25 };
    private int speedIdx = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        store = new CurveStore(this);

        applyInsets(findViewById(R.id.root));

        pageCapture = findViewById(R.id.page_capture);
        pageResult  = findViewById(R.id.page_result);
        pageLibrary = findViewById(R.id.page_library);

        capture = (CaptureView) findViewById(R.id.capture);
        capture.setListener(this);
        captureOverlay = findViewById(R.id.capture_overlay);
        captureErr = (TextView) findViewById(R.id.capture_err);

        curveView = (CurveView) findViewById(R.id.curve);
        wall = (PreviewWall) findViewById(R.id.wall);
        scrub = (SeekBar) findViewById(R.id.scrub);
        headline = (TextView) findViewById(R.id.r_headline);
        reason = (TextView) findViewById(R.id.r_reason);
        stats = (TextView) findViewById(R.id.r_stats);
        curveHint = (TextView) findViewById(R.id.curve_hint);
        durLabel = (TextView) findViewById(R.id.dur_label);
        chipBezier = (TextView) findViewById(R.id.chip_bezier);
        chipSpring = (TextView) findViewById(R.id.chip_spring);
        chipPath = (TextView) findViewById(R.id.chip_poly);
        btnSnap = (TextView) findViewById(R.id.btn_snap);
        btnMirror = (TextView) findViewById(R.id.btn_mirror);
        btnClamp = (TextView) findViewById(R.id.btn_clamp);
        btnPlay = (TextView) findViewById(R.id.btn_play);
        btnSpeed = (TextView) findViewById(R.id.btn_speed);
        btnGhost = (TextView) findViewById(R.id.btn_ghost);
        btnHaptic = (TextView) findViewById(R.id.btn_haptic);
        exportsBox = (LinearLayout) findViewById(R.id.exports);
        grid = (GridView) findViewById(R.id.grid);
        libEmpty = (TextView) findViewById(R.id.lib_empty);
        libSearch = (EditText) findViewById(R.id.lib_search);

        curveView.setListener(new CurveView.Listener() {
            @Override public void onEdited(Bezier nb) {
                if (fit == null) return;
                fit = fit.withBezier(nb, "Hand-tuned from your flick.");
                refreshForModel();
            }
        });

        wall.setProgressListener(new PreviewWall.ProgressListener() {
            @Override public void onProgress(double t) {
                scrub.setProgress((int) Math.round(t * 1000));
                curveView.setMarker(t);
            }
        });

        scrub.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (!user) return;
                double t = p / 1000.0;
                wall.setProgress(t);
                curveView.setMarker(t);
                btnPlay.setText("Play");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { wall.pause(); }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        click(R.id.btn_library, new Runnable() { public void run() { showLibrary(); } });
        click(R.id.btn_back,    new Runnable() { public void run() { showCapture(); } });
        click(R.id.btn_again,   new Runnable() { public void run() { showCapture(); } });
        click(R.id.btn_save,    new Runnable() { public void run() { askSave(); } });
        click(R.id.btn_kotlin,  new Runnable() { public void run() { exportKotlin(); } });

        chipBezier.setOnClickListener(modelClick(FitResult.Model.BEZIER));
        chipSpring.setOnClickListener(modelClick(FitResult.Model.SPRING));
        chipPath.setOnClickListener(modelClick(FitResult.Model.PATH));

        click(R.id.btn_snap,   new Runnable() { public void run() { refineBezier(0); } });
        click(R.id.btn_mirror, new Runnable() { public void run() { refineBezier(1); } });
        click(R.id.btn_clamp,  new Runnable() { public void run() { refineBezier(2); } });
        click(R.id.btn_dur_down, new Runnable() { public void run() { nudgeDuration(-10); } });
        click(R.id.btn_dur_up,   new Runnable() { public void run() { nudgeDuration(10); } });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wall.toggle();
                btnPlay.setText(wall.isPlaying() ? "Pause" : "Play");
            }
        });
        btnSpeed.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                speedIdx = (speedIdx + 1) % SPEEDS.length;
                wall.setSpeed(SPEEDS[speedIdx]);
                btnSpeed.setText(Fmt.n(SPEEDS[speedIdx]) + "x");
                if (wall.isPlaying()) wall.play();
            }
        });
        btnGhost.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wall.setGhost(!wall.ghost());
                styleChip(btnGhost, wall.ghost());
            }
        });
        btnHaptic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (fit == null) return;
                if (!Haptics.play(MainActivity.this, fit.easing(), Exporters.durationMs(fit))) {
                    toast("No vibration motor on this device");
                }
            }
        });

        libSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { reloadLibrary(); }
        });

        adapter = new LibraryAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                fit = curves.get(pos).toFit();
                fit.reason = "Loaded from library.";
                showResult();
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                curveOptions(curves.get(pos));
                return true;
            }
        });

        styleChip(btnGhost, true);
        showCapture();
    }

    private void click(int id, final Runnable r) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
    }

    private void applyInsets(final View root) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top, bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
                    top = i.top; bottom = i.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    private View.OnClickListener modelClick(final FitResult.Model m) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (fit == null) return;
                fit.model = m;
                fit.reason = fit.describe(m);
                refreshForModel();
            }
        };
    }

    // ---- editor refinements -------------------------------------------------

    private void refineBezier(int op) {
        if (fit == null) return;
        if (fit.model != FitResult.Model.BEZIER) {
            toast("Refinements apply to bezier curves");
            return;
        }
        Bezier b = fit.bezier;
        Bezier nb;
        String what;
        switch (op) {
            case 0: nb = b.snapped();  what = "Snapped to 0.05 steps."; break;
            case 1: nb = b.mirrored(); what = "Made symmetric."; break;
            default: nb = b.clamped(); what = "Clamped inside 0 to 1."; break;
        }
        fit = fit.withBezier(nb, what);
        refreshForModel();
    }

    private void nudgeDuration(int deltaMs) {
        if (fit == null) return;
        double d = fit.durationMs() + deltaMs;
        fit.durationOverrideMs = Math.max(50, Math.min(5000, d));
        refreshForModel();
    }

    // ---- pages --------------------------------------------------------------

    private void showCapture() {
        wall.pause();
        Haptics.stop(this);
        pageCapture.setVisibility(View.VISIBLE);
        pageResult.setVisibility(View.GONE);
        pageLibrary.setVisibility(View.GONE);
        capture.clear();
        captureOverlay.setVisibility(View.VISIBLE);
        captureErr.setVisibility(View.INVISIBLE);
    }

    private void showResult() {
        pageCapture.setVisibility(View.GONE);
        pageResult.setVisibility(View.VISIBLE);
        pageLibrary.setVisibility(View.GONE);
        ((ScrollView) pageResult).scrollTo(0, 0);
        refreshForModel();
        wall.setSpeed(SPEEDS[speedIdx]);
        wall.play();
        btnPlay.setText("Pause");
    }

    private void showLibrary() {
        wall.pause();
        pageCapture.setVisibility(View.GONE);
        pageResult.setVisibility(View.GONE);
        pageLibrary.setVisibility(View.VISIBLE);
        reloadLibrary();
    }

    // ---- capture callbacks --------------------------------------------------

    @Override public void onStarted() {
        captureOverlay.setVisibility(View.GONE);
        captureErr.setVisibility(View.INVISIBLE);
    }

    @Override public void onCaptured(Trace t) {
        try {
            fit = CurveFitter.fit(t);
        } catch (Exception e) {
            onRejected("Could not read that flick");
            return;
        }
        showResult();
    }

    @Override public void onRejected(String r) {
        captureErr.setText(r);
        captureErr.setVisibility(View.VISIBLE);
        captureOverlay.setVisibility(View.VISIBLE);
    }

    // ---- result rendering ---------------------------------------------------

    private int modelColor(FitResult.Model m) {
        switch (m) {
            case SPRING: return C_SPRING;
            case PATH:   return C_PATH;
            default:     return C_BEZIER;
        }
    }

    private void refreshForModel() {
        if (fit == null) return;

        headline.setText(fit.headline());
        headline.setTextColor(modelColor(fit.model));
        reason.setText(fit.reason);

        curveView.setFit(fit);
        boolean isBezier = fit.model == FitResult.Model.BEZIER;
        curveHint.setText(isBezier
                ? "Faint line is your flick. Drag the two dots to refine."
                : "Faint line is your flick, solid is the fit.");

        int refineVis = isBezier ? View.VISIBLE : View.GONE;
        btnSnap.setVisibility(refineVis);
        btnMirror.setVisibility(refineVis);
        btnClamp.setVisibility(refineVis);
        durLabel.setText(Exporters.durationMs(fit) + "ms");

        styleChip(chipBezier, isBezier);
        styleChip(chipSpring, fit.model == FitResult.Model.SPRING);
        styleChip(chipPath, fit.model == FitResult.Model.PATH);

        stats.setText(buildStats());

        wall.setEasing(fit.easing(), Exporters.durationMs(fit));
        buildExports();
    }

    private String buildStats() {
        Trace t = fit.trace;
        StringBuilder sb = new StringBuilder();
        sb.append("Duration   ").append(Exporters.durationMs(fit)).append("ms");
        if (fit.durationOverrideMs > 0) sb.append("  (tuned)");
        sb.append('\n');
        sb.append("Accuracy   ").append(Fmt.f(fit.accuracyPct(), 1)).append("%   (")
          .append(fit.paramCount()).append(" params)\n");

        if (t.hasAnticipation()) {
            sb.append("Anticipation   ").append(Fmt.pct(t.anticipation))
              .append(" pull-back at ").append(Math.round(t.anticipationMs)).append("ms\n");
        }
        if (t.hasOvershoot()) {
            sb.append("Overshoot   ").append(Fmt.pct(t.overshoot))
              .append(", settles in ").append(Math.round(t.settleMs)).append("ms\n");
        }

        String character;
        if (t.backLoad > 0.62) character = "back-loaded";
        else if (t.backLoad < 0.28) character = "front-loaded";
        else character = "even";
        sb.append("Character   ").append(character).append(", ")
          .append(Fmt.pct(t.backLoad)).append(" of the distance in the last 40%\n");

        if (fit.model == FitResult.Model.SPRING) {
            sb.append("Spring   stiffness ").append(Fmt.f(fit.spring.stiffness(), 0))
              .append(", damping ").append(Fmt.n(fit.spring.zeta)).append("\n");
        }
        if (fit.model == FitResult.Model.PATH) {
            sb.append("Path   ").append(fit.path.segments()).append(" cubic segments, ")
              .append(fit.path.knotCount()).append(" knots\n");
        }

        sb.append("Error   bezier ").append(Fmt.f(fit.rmsBezier, 3))
          .append("  spring ").append(Fmt.f(fit.rmsSpring, 3))
          .append("  path ").append(Fmt.f(fit.rmsPath, 3));
        return sb.toString();
    }

    private void buildExports() {
        exportsBox.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (final Exporters.Item item : Exporters.all(fit)) {
            View row = inf.inflate(R.layout.item_export, exportsBox, false);
            ((TextView) row.findViewById(R.id.ex_label)).setText(item.label);
            ((TextView) row.findViewById(R.id.ex_code)).setText(item.code);
            row.findViewById(R.id.ex_copy).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { copy(item.label, item.code); }
            });
            exportsBox.addView(row);
        }
    }

    private void styleChip(TextView chip, boolean on) {
        chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        chip.setTextColor(on ? C_BEZIER : C_TEXT);
    }

    // ---- library ------------------------------------------------------------

    private void reloadLibrary() {
        String q = libSearch.getText().toString();
        curves.clear();
        curves.addAll(store.search(q));
        adapter.notifyDataSetChanged();
        boolean empty = curves.isEmpty();
        libEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        libEmpty.setText(q.trim().length() > 0
                ? "Nothing matches that."
                : "No curves yet.\nFlick one and save it.");
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(C_TEXT);
        e.setHintTextColor(Color.parseColor("#FF7C8794"));
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        e.setPadding(pad, pad, pad, pad);
        return e;
    }

    private void askSave() {
        if (fit == null) return;
        final EditText name = field("Snappy settle", "");
        final EditText tags = field("tags: ui, snappy", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(name);
        box.addView(tags);

        new AlertDialog.Builder(this)
                .setTitle("Name this curve")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        if (n.length() == 0) n = "Curve " + (store.count() + 1);
                        store.insert(SavedCurve.from(n, tags.getText().toString().trim(), fit));
                        toast("Saved " + n);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void curveOptions(final SavedCurve c) {
        final String favLabel = c.favourite ? "Remove favourite" : "Favourite";
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new CharSequence[]{ favLabel, "Rename", "Delete" },
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    store.setFavourite(c.id, !c.favourite);
                                    reloadLibrary();
                                } else if (which == 1) {
                                    askRename(c);
                                } else {
                                    confirmDelete(c);
                                }
                            }
                        })
                .show();
    }

    private void askRename(final SavedCurve c) {
        final EditText input = field("Name", c.name);
        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (n.length() > 0) { store.rename(c.id, n); reloadLibrary(); }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final SavedCurve c) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + c.name + "?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        store.delete(c.id);
                        reloadLibrary();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportKotlin() {
        // curves is the search result. The button exports the whole library.
        String code = CurveStore.kotlinLibrary(store.search(""));
        copy("Kotlin library", code);
        TextView tv = new TextView(this);
        tv.setText(code);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Motion.kt  (copied)")
                .setView(sv)
                .setPositiveButton("Done", null)
                .show();
    }

    private class LibraryAdapter extends BaseAdapter {
        @Override public int getCount() { return curves.size(); }
        @Override public Object getItem(int i) { return curves.get(i); }
        @Override public long getItemId(int i) { return curves.get(i).id; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_curve, parent, false);
            }
            SavedCurve c = curves.get(pos);
            FitResult.Model m;
            try { m = FitResult.Model.valueOf(c.model); }
            catch (Exception e) { m = FitResult.Model.BEZIER; }

            ((ThumbView) v.findViewById(R.id.thumb)).setCurve(c.traceStops, modelColor(m));
            ((TextView) v.findViewById(R.id.thumb_name))
                    .setText((c.favourite ? "★ " : "") + c.name);
            String meta = c.model.toLowerCase() + " · " + Math.round(c.durationMs) + "ms";
            if (c.tags.length() > 0) meta += " · " + c.tags;
            ((TextView) v.findViewById(R.id.thumb_meta)).setText(meta);
            return v;
        }
    }

    // ---- helpers ------------------------------------------------------------

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            toast(label + " copied");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override public void onBackPressed() {
        if (pageLibrary.getVisibility() == View.VISIBLE
                || pageResult.getVisibility() == View.VISIBLE) {
            showCapture();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        wall.pause();
        Haptics.stop(this);
    }
}
