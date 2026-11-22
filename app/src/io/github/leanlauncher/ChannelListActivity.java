package io.github.leanlauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Map;

/**
 * Senderliste wie im Programmheft: links Sender + laufende Sendung,
 * rechts Vorschau-Stream mit Programminfo.
 *
 * Performance:
 *  - Views werden einmal gebaut; Fokus-Wechsel tauscht nur Text/Farbe.
 *  - Genau EIN VideoView, wird fuer jeden Sender wiederverwendet.
 *  - EPG kommt aus dem Cache (kein Download im UI-Thread).
 */
public class ChannelListActivity extends Activity {

    private LinearLayout listCol;
    private FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private float dp;

    private VideoView preview;
    private FrameLayout previewBox;
    private TextView infoName, infoNow, infoRest, infoNextLabel, infoNext;

    private static final long SCREENSAVER_MS = 3 * 60 * 1000;

    private final View[] rows = new View[Channels.NAMES.length];
    private int selected = 0;
    private long lastPlayAt = 0;
    private final Runnable screensaver = new Runnable() {
        public void run() { startArt(); }
    };
    private int previewIdx = -1;
    private Map<String, Epg.Now> epg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        buildUi();
    }

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        epg = Epg.cached(this);
        applyEpg(epg);
        Epg.refreshIfStale(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        epg = Epg.cached(ChannelListActivity.this);
                        applyEpg(epg);
                    }
                });
            }
        });

        ui.postDelayed(new Runnable() {
            public void run() {
                if (selected >= 0 && selected < rows.length && rows[selected] != null) {
                    rows[selected].requestFocus();
                }
            }
        }, 120);

        resetScreensaver();
    }

    private void resetScreensaver() {
        ui.removeCallbacks(screensaver);
        ui.postDelayed(screensaver, SCREENSAVER_MS);
    }

    private void startArt() {
        try {
            Intent i = new Intent();
            i.setClassName("io.github.artmode", "io.github.artmode.ArtActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(screensaver);
        if (preview != null) {
            try { preview.pause(); } catch (RuntimeException ignored) { }
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) hideSystemUi();
    }

    // ---------------- Aufbau ----------------

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(40), dp(18), dp(40), dp(18));

        // Kopf
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(12));

        TextView t = new TextView(this);
        t.setText("LIVE-TV");
        t.setTextColor(Ui.ACCENT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = new TextView(this);
        hint.setText("↑↓ Sender    OK abspielen    BACK zurück");
        hint.setTextColor(Ui.TEXT_DIM);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        head.addView(hint);

        page.addView(head);

        // Zwei Spalten
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);

        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f);
        lp1.rightMargin = dp(20);
        cols.addView(listCol, lp1);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(dp(20), dp(16), dp(20), dp(16));
        infoCol.setBackground(Ui.round(Ui.CARD, dp, 12));
        cols.addView(infoCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        page.addView(cols, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        buildInfoColumn(infoCol);
        buildRows();
    }

    private void buildRows() {
        for (int i = 0; i < Channels.NAMES.length; i++) {
            final int idx = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), dp(6), dp(12), dp(6));
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setBackground(Ui.card(dp, 9));

            TextView nm = new TextView(this);
            nm.setText(Channels.NAMES[i]);
            nm.setTextColor(Ui.TEXT);
            nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            nm.setTypeface(Typeface.DEFAULT_BOLD);
            nm.setSingleLine(true);
            row.addView(nm, new LinearLayout.LayoutParams(dp(150),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // rechts: Titel + Fortschrittsbalken darunter
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setPadding(0, 0, 0, 0);

            TextView prog = new TextView(this);
            prog.setText("");
            prog.setTextColor(Ui.TEXT_DIM);
            prog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            prog.setSingleLine(true);
            right.addView(prog, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Fortschrittsbalken: gefuellter Teil + Rest
            FrameLayout bar = new FrameLayout(this);
            GradientDrawable track = new GradientDrawable();
            track.setColor(0xFF2A2A30);
            track.setCornerRadius(dp(2));
            bar.setBackground(track);

            View fill = new View(this);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(Ui.ACCENT);
            fillBg.setCornerRadius(dp(2));
            fill.setBackground(fillBg);
            bar.addView(fill, new FrameLayout.LayoutParams(0, dp(3)));

            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            barLp.topMargin = dp(3);
            right.addView(bar, barLp);

            row.addView(right, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            listCol.addView(row, lp);
            rows[i] = row;

            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    LinearLayout r = (LinearLayout) v;
                    v.setBackground(Ui.round(has ? Ui.CARD_FOCUS : Ui.CARD, dp, 9));
                    if (r.getChildCount() > 0) {
                        ((TextView) r.getChildAt(0)).setTextColor(
                                has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
                    }
                    if (r.getChildCount() > 1) {
                        ((TextView) r.getChildAt(1)).setTextColor(
                                has ? Color.rgb(60, 44, 24) : Ui.TEXT_DIM);
                    }
                    v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                            .setDuration(80).start();
                    if (has) {
                        selected = idx;
                        showInfo(idx);
                    }
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Doppel-Ausloesung verhindern (Key-Down + gepufferter Click)
                    long now = System.currentTimeMillis();
                    if (now - lastPlayAt < 900) return;
                    lastPlayAt = now;
                    play(idx);
                }
            });
        }
    }

    private void buildInfoColumn(LinearLayout infoCol) {
        // Vorschau
        previewBox = new FrameLayout(this);
        previewBox.setBackground(Ui.round(Color.BLACK, dp, 8));
        previewBox.setClipToOutline(true);
        previewBox.setClipChildren(true);
        // harter Rechteck-Zuschnitt (clipToOutline allein schneidet bei
        // abgerundeten Ecken nicht zuverlaessig)
        previewBox.setLayerType(View.LAYER_TYPE_NONE, null);

        preview = new VideoView(this);
        FrameLayout.LayoutParams vvp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        vvp.gravity = Gravity.CENTER;
        previewBox.addView(preview, vvp);

        TextView badge = new TextView(this);
        badge.setText("● LIVE");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(Ui.round(Ui.LIVE, dp, 4));
        badge.setPadding(dp(6), dp(2), dp(6), dp(2));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.gravity = Gravity.TOP | Gravity.LEFT;
        bl.leftMargin = dp(7); bl.topMargin = dp(7);
        previewBox.addView(badge, bl);

        LinearLayout.LayoutParams pvp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        pvp.bottomMargin = dp(12);
        infoCol.addView(previewBox, pvp);

        // 16:9 erzwingen
        previewBox.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int bo,
                                       int ol, int ot, int or, int ob) {
                int w = r - l;
                if (w <= 0) return;
                int wantH = Math.round(w * 9f / 16f);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp.height != wantH) {
                    lp.height = wantH;
                    v.setLayoutParams(lp);
                }
            }
        });

        infoName = new TextView(this);
        infoName.setTextColor(Ui.TEXT);
        infoName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        infoName.setTypeface(Typeface.DEFAULT_BOLD);
        infoCol.addView(infoName);

        infoCol.addView(microLabel("JETZT"));
        infoNow = new TextView(this);
        infoNow.setTextColor(Ui.TEXT);
        infoNow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        infoNow.setMaxLines(3);
        infoCol.addView(infoNow);

        infoRest = new TextView(this);
        infoRest.setTextColor(Ui.TEXT_DIM);
        infoRest.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        infoRest.setPadding(0, dp(3), 0, 0);
        infoCol.addView(infoRest);

        infoNextLabel = microLabel("DANACH");
        infoCol.addView(infoNextLabel);
        infoNext = new TextView(this);
        infoNext.setTextColor(Ui.TEXT_DIM);
        infoNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        infoNext.setMaxLines(2);
        infoCol.addView(infoNext);
    }

    private TextView microLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.rgb(110, 110, 122));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.16f);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    // ---------------- Inhalt ----------------

    private void applyEpg(Map<String, Epg.Now> map) {
        if (map == null) return;
        for (int i = 0; i < Channels.EPG_IDS.length && i < rows.length; i++) {
            LinearLayout row = (LinearLayout) rows[i];
            Epg.Now n = map.get(Channels.EPG_IDS[i]);
            if (row.getChildCount() > 1) {
                LinearLayout right = (LinearLayout) row.getChildAt(1);
                TextView p = (TextView) right.getChildAt(0);
                if (n != null && n.title.length() > 0) {
                    p.setText(n.title);
                    setBar(right, n.progress());
                } else {
                    p.setText("");
                    setBar(right, -1f);
                }
            }
        }
        showInfo(selected);
    }

    /** Setzt den Fortschrittsbalken (0..1), -1 = unbekannt. */
    private void setBar(LinearLayout right, float p) {
        if (right.getChildCount() < 2) return;
        FrameLayout bar = (FrameLayout) right.getChildAt(1);
        if (bar.getChildCount() == 0) return;
        View fill = bar.getChildAt(0);
        int w = bar.getWidth();
        if (w <= 0 || p < 0f) {
            fill.setLayoutParams(new FrameLayout.LayoutParams(0, dp(3)));
            return;
        }
        if (p > 1f) p = 1f;
        int fw = (int) (w * p);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(fw, dp(3));
        fill.setLayoutParams(lp);
    }

    private void showInfo(int idx) {
        if (infoName == null || idx < 0 || idx >= Channels.NAMES.length) return;

        infoName.setText(Channels.NAMES[idx]);
        infoName.setTextColor(Channels.COLORS[idx]);

        Epg.Now n = epg == null ? null : epg.get(Channels.EPG_IDS[idx]);
        if (n != null && n.title.length() > 0) {
            infoNow.setText(n.title);
            if (n.minutesLeft >= 0) {
                infoRest.setText("noch " + n.minutesLeft + " Minuten");
                infoRest.setVisibility(View.VISIBLE);
            } else {
                infoRest.setVisibility(View.GONE);
            }
        } else {
            infoNow.setText("Programminfo wird geladen ...");
            infoRest.setVisibility(View.GONE);
        }

        boolean hasNext = n != null && n.next.length() > 0 && !n.next.equals(n.title);
        infoNextLabel.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        infoNext.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        if (hasNext) infoNext.setText(n.next);

        switchPreview(idx);
    }

    private void switchPreview(int idx) {
        if (preview == null || idx < 0 || idx >= Channels.URLS.length) return;
        if (idx == previewIdx && preview.isPlaying()) return;
        previewIdx = idx;

        try { preview.stopPlayback(); } catch (RuntimeException ignored) { }

        preview.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                mp.setVolume(0.35f, 0.35f);
                mp.setLooping(false);
                final int vw = mp.getVideoWidth(), vh = mp.getVideoHeight();
                previewBox.post(new Runnable() {
                    public void run() { fitPreview(vw, vh); }
                });
                preview.start();
            }
        });
        preview.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) { return true; }
        });
        preview.setVideoURI(Uri.parse(Channels.URLS[idx]));
    }

    /** Fuellt die 16:9-Box komplett - kein schwarzer Rand. */
    private void fitPreview(int vw, int vh) {
        if (previewBox == null || preview == null || vw <= 0 || vh <= 0) return;
        int bw = previewBox.getWidth(), bh = previewBox.getHeight();
        if (bw <= 0 || bh <= 0) return;

        float vAR = (float) vw / (float) vh;
        float bAR = (float) bw / (float) bh;

        // Immer auf die groessere Achse zoomen, damit beide Kanten bündig sind.
        float scale = Math.max(vAR / bAR, bAR / vAR);
        scale *= 1.04f;                 // kleiner Ueberstand, wird beschnitten

        preview.setScaleX(scale);
        preview.setScaleY(scale);
        preview.setTranslationX(0f);
        preview.setTranslationY(0f);
    }

    private void play(int idx) {
        Intent i = new Intent(this, LiveActivity.class);
        i.putExtra("name", Channels.NAMES[idx]);
        i.putExtra("url", Channels.URLS[idx]);
        startActivity(i);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        resetScreensaver();
        if (code == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (preview != null) {
            try { preview.stopPlayback(); } catch (RuntimeException ignored) { }
        }
    }
}
