package io.github.leanlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Nachrichten auf Abruf: die neuesten Podcast-Folgen von Tagesschau, Tagesthemen
 * und Deutschlandfunk. Antippen spielt die Folge ab (Audio).
 *
 * Design: tvOS - schwarz, grosse Typo, wenig Farbe, viel Weissraum.
 */
public class NewsActivity extends Activity {

    private LinearLayout listCol;
    private TextView status, nowPlaying, nowTime;
    private ProgressBar busy;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private float dp;

    private MediaPlayer player;
    private int playingRow = -1;
    private final List<View> rows = new ArrayList<View>();
    private final List<Podcasts.Folge> data = new ArrayList<Podcasts.Folge>();

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (player != null && player.isPlaying() && nowTime != null) {
                nowTime.setText(fmt(player.getCurrentPosition()) + " / " + fmt(player.getDuration()));
                ui.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        buildUi();
        load();
    }

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    private String fmt(int ms) {
        if (ms <= 0) return "0:00";
        int s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        hideSystemUi();
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

    // ---------------- UI ----------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(72), dp(40), dp(72), dp(32));

        // Kopf: tvOS - grosse duenne Ueberschrift
        TextView h = new TextView(this);
        h.setText("Nachrichten");
        h.setTextColor(Ui.TEXT);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        h.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        page.addView(h);

        TextView sub = new TextView(this);
        sub.setText("Die neuesten Ausgaben zum Anhören");
        sub.setTextColor(Ui.TEXT_DIM);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setPadding(0, dp(4), 0, dp(24));
        page.addView(sub);

        // Liste
        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        page.addView(listCol);

        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Fusszeile: laufende Wiedergabe
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(18), dp(12), dp(18), dp(12));
        foot.setBackground(Ui.round(Ui.CARD, dp, 10));

        busy = new ProgressBar(this);
        busy.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(26), dp(26));
        blp.rightMargin = dp(14);
        foot.addView(busy, blp);

        LinearLayout footTxt = new LinearLayout(this);
        footTxt.setOrientation(LinearLayout.VERTICAL);
        nowPlaying = new TextView(this);
        nowPlaying.setTextColor(Ui.TEXT);
        nowPlaying.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nowPlaying.setText("Nichts ausgewählt");
        footTxt.addView(nowPlaying);

        nowTime = new TextView(this);
        nowTime.setTextColor(Ui.TEXT_DIM);
        nowTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footTxt.addView(nowTime);

        foot.addView(footTxt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = new TextView(this);
        hint.setText("OK abspielen / pausieren      BACK zurück");
        hint.setTextColor(Ui.TEXT_DIM);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        foot.addView(hint);

        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(14);
        page.addView(foot, flp);

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setText("Lade Nachrichten ...");
        status.setTextColor(Ui.TEXT_DIM);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER;
        root.addView(status, sp);

        setContentView(root);
    }

    // ---------------- Laden ----------------

    private void load() {
        // Erster Aufruf: liefert sofort den (evtl. leeren) Cache und startet den Download.
        Podcasts.get(data, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        // Callback kommt NACH dem Download - hier ist data gefuellt.
                        fillFromCache();
                    }
                });
            }
        });
        // Falls schon Daten im Cache lagen, sofort zeichnen.
        fillFromCache();
    }

    /** Zeichnet die Liste, wenn Daten vorhanden sind. */
    private void fillFromCache() {
        List<Podcasts.Folge> fresh = new ArrayList<Podcasts.Folge>();
        Podcasts.get(fresh, null);        // synchron aus Cache
        if (!fresh.isEmpty()) {
            data.clear();
            data.addAll(fresh);
            status.setVisibility(View.GONE);
            renderRows();
        } else if (status != null) {
            status.setText("Lade Nachrichten ...");
            status.setVisibility(View.VISIBLE);
        }
    }

    private void renderRows() {
        listCol.removeAllViews();
        rows.clear();

        for (int i = 0; i < data.size(); i++) {
            final int idx = i;
            final Podcasts.Folge f = data.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(11), dp(16), dp(11));
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setBackground(Ui.card(dp, 10));

            // Sender-Label
            TextView show = new TextView(this);
            show.setText(f.showLabel);
            show.setTextColor(Ui.TEXT_DIM);
            show.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            show.setTypeface(Typeface.DEFAULT_BOLD);
            show.setSingleLine(true);
            row.addView(show, new LinearLayout.LayoutParams(dp(210),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // Titel
            TextView t = new TextView(this);
            t.setText(f.title);
            t.setTextColor(Ui.TEXT);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setSingleLine(true);
            row.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Dauer
            TextView d = new TextView(this);
            d.setText(f.durationSec > 0 ? (f.durationSec / 60) + " Min" : "");
            d.setTextColor(Ui.TEXT_DIM);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            d.setPadding(dp(14), 0, 0, 0);
            row.addView(d);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            listCol.addView(row, lp);
            rows.add(row);

            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    LinearLayout r = (LinearLayout) v;
                    v.setBackground(Ui.round(has ? Ui.CARD_FOCUS : Ui.CARD, dp, 10));
                    for (int c = 0; c < r.getChildCount(); c++) {
                        View ch = r.getChildAt(c);
                        if (ch instanceof TextView) {
                            ((TextView) ch).setTextColor(has ? Ui.TEXT_ON_FOCUS
                                    : (c == 0 || c == 2 ? Ui.TEXT_DIM : Ui.TEXT));
                        }
                    }
                    v.animate().scaleX(has ? 1.01f : 1f).scaleY(has ? 1.01f : 1f)
                            .setDuration(80).start();
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { play(idx, v); }
            });
        }

        listCol.post(new Runnable() {
            public void run() {
                if (!rows.isEmpty()) rows.get(0).requestFocus();
            }
        });
    }

    // ---------------- Wiedergabe ----------------

    private void play(int idx, View row) {
        if (idx < 0 || idx >= data.size()) return;
        Podcasts.Folge f = data.get(idx);

        // gleiche Folge -> Pause/Weiter
        if (idx == playingRow && player != null) {
            if (player.isPlaying()) {
                player.pause();
                nowPlaying.setText("Pause · " + f.title);
                ui.removeCallbacks(ticker);
            } else {
                player.start();
                nowPlaying.setText(f.title);
                ui.post(ticker);
            }
            return;
        }

        stopPlayer();
        playingRow = idx;

        busy.setVisibility(View.VISIBLE);
        nowPlaying.setText(f.title);
        nowTime.setText("lädt ...");

        player = new MediaPlayer();
        try {
            player.setDataSource(f.url);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mp) {
                    busy.setVisibility(View.GONE);
                    mp.start();
                    ui.post(ticker);
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    nowPlaying.setText("Fertig");
                    nowTime.setText("");
                    ui.removeCallbacks(ticker);
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    busy.setVisibility(View.GONE);
                    nowPlaying.setText("Wiedergabe-Fehler (" + what + ")");
                    nowTime.setText("");
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            busy.setVisibility(View.GONE);
            nowPlaying.setText("Fehler: " + e.getClass().getSimpleName());
            nowTime.setText("");
        }
    }

    private void stopPlayer() {
        ui.removeCallbacks(ticker);
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) { }
            try { player.release(); } catch (RuntimeException ignored) { }
            player = null;
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            stopPlayer();
            finish();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Audio darf weiterlaufen, wenn der Nutzer nur kurz woanders hinschaut -
        // beim Verlassen der App wird in onDestroy gestoppt.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayer();
        ui.removeCallbacksAndMessages(null);
    }
}
