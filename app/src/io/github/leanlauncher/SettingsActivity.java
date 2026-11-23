package io.github.leanlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Ort fuer das Wetter einstellen.
 *
 * Zwei Wege:
 *   - AUTOMATISCH: einmalige Ortung, Ergebnis wird gespeichert
 *   - STADT WAEHLEN: aus einer Liste grosser Staedte
 *
 * Warum eine eigene Auswahl statt freier Eingabe: auf einem Fernseher tippt
 * niemand gern. Die Liste deckt die haeufigen Faelle ab, und wer woanders
 * wohnt, nimmt die automatische Ortung.
 */
public class SettingsActivity extends Activity {

    private float dp;
    private LinearLayout cityCol;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

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
        HomeService.pauseFor(10 * 60 * 1000);
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

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(22), dp(56), dp(22));

        TextView head = new TextView(this);
        head.setText("EINSTELLUNGEN");
        head.setTextColor(Ui.ACCENT);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.18f);
        page.addView(head);

        TextView title = new TextView(this);
        title.setText("Ort für das Wetter");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setPadding(0, dp(4), 0, dp(4));
        page.addView(title);

        Place.Info cur = Place.load(this);
        status = new TextView(this);
        status.setText(currentText(cur));
        status.setTextColor(Ui.TEXT_DIM);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setPadding(0, 0, 0, dp(14));
        page.addView(status);

        // ---- Automatisch ----
        View auto = row("Automatisch orten",
                "Einmal ermitteln, dann gespeichert", Ui.ACCENT, new Runnable() {
                    public void run() { doLocate(); }
                });
        page.addView(auto, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        auto.requestFocus();

        TextView or = new TextView(this);
        or.setText("ODER STADT WÄHLEN");
        or.setTextColor(Color.rgb(110, 110, 124));
        or.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        or.setTypeface(Typeface.DEFAULT_BOLD);
        or.setLetterSpacing(0.14f);
        or.setPadding(dp(4), dp(16), 0, dp(6));
        page.addView(or);

        cityCol = new LinearLayout(this);
        cityCol.setOrientation(LinearLayout.VERTICAL);

        // 2 Spalten: 14 Staedte passen in 7 Reihen
        LinearLayout row = null;
        for (int i = 0; i < Place.CITIES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
                rlp.bottomMargin = dp(4);
                cityCol.addView(row, rlp);
            }
            final String name = Place.CITIES[i][0];
            final double lat = Double.parseDouble(Place.CITIES[i][1]);
            final double lon = Double.parseDouble(Place.CITIES[i][2]);
            View c = row(name, name.equals(cur.city) ? "aktuell" : "", Ui.TEXT,
                    new Runnable() {
                        public void run() {
                            Place.setCity(SettingsActivity.this, name, lat, lon);
                            Place.Info i2 = Place.load(SettingsActivity.this);
                            status.setText(currentText(i2));
                            android.util.Log.i("LEANLOC", "Stadt gesetzt: " + name);
                        }
                    });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            clp.rightMargin = dp(4);
            row.addView(c, clp);
        }
        page.addView(cityCol);

        TextView hint = new TextView(this);
        hint.setText("BACK  =  zurück.  Die Auswahl gilt sofort beim nächsten Wetterabruf.");
        hint.setTextColor(Color.rgb(96, 96, 110));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(dp(4), dp(16), 0, 0);
        page.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private String currentText(Place.Info i) {
        if (i.manual) return "Fest eingestellt: " + i.city + "  (" + Place.coordText(i) + ")";
        if (i.located) return "Automatisch ermittelt: " + i.city + "  (" + Place.coordText(i) + ")";
        return "Noch nicht festgelegt – aktuell " + i.city;
    }

    private void doLocate() {
        status.setText("Ortung läuft … (max. 8 Sekunden)");
        Place.setAuto(this);
        Place.locateIfNeeded(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        Place.Info i = Place.load(SettingsActivity.this);
                        status.setText(i.located
                                ? currentText(i)
                                : "Ortung nicht möglich. Bitte eine Stadt wählen.");
                    }
                });
            }
        });
    }

    /** Zeile mit Titel, Untertitel und farbigem Balken links. */
    private View row(final String titel, final String sub, final int akzent,
                     final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.card(dp, 10));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(dp(4), dp(4), dp(12), dp(4));

        View stripe = new View(this);
        stripe.setBackground(Ui.round(akzent, dp, 1.5f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(3),
                ViewGroup.LayoutParams.MATCH_PARENT);
        slp.topMargin = dp(6);
        slp.bottomMargin = dp(6);
        slp.rightMargin = dp(10);
        card.addView(stripe, slp);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.CENTER_VERTICAL);

        final TextView t = new TextView(this);
        t.setText(titel);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        txt.addView(t);

        final TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Ui.TEXT_DIM);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        s.setIncludeFontPadding(false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(s);

        card.addView(txt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(Ui.focusRing(dp, has ? Ui.CARD_FOCUS : Ui.CARD,
                        Ui.ACCENT, 10));
                t.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
                s.setTextColor(has ? Color.rgb(80, 56, 24) : Ui.TEXT_DIM);
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                        .setDuration(90).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        return card;
    }
}
