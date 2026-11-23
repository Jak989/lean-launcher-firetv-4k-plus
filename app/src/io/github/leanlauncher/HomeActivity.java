package io.github.leanlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Startbildschirm im tvOS-Stil: schwarz, grosse duenne Typografie, wenig Farbe.
 *
 * Aufbau:
 *   Kopf: Uhrzeit (gross, duenn) + Datum + Wetter
 *   Zwei grosse Kacheln: Live-TV und Nachrichten
 *   App-Raster
 *
 * Kein Scrolling, alle Views werden einmal gebaut.
 */
public class HomeActivity extends Activity {

    private static final String ART_PKG = "io.github.artmode";
    private static final String ART_CLS = "io.github.artmode.ArtActivity";
    private static final long SCREENSAVER_MS = 3 * 60 * 1000;

    /** Feste View-ID fuer die SZ-Leiste, damit das D-Pad gezielt dorthin springt. */
    private static final int NEWS_TILE_ID = 0x7f0f0001;
    /** Kantenlaenge des App-Symbols und Innenabstand der Kachel. */
    private static final int APP_ICON = 52;
    private static final int APP_PAD = 8;

    /** ID der ersten grossen Kachel (Ziel fuer "runter" aus der SZ-Leiste). */
    private static final int KACHEL_ID = 0x7f0f0002;
    /** ID des Tagesschau-Buttons (Fokus nach "runter" aus Live-TV). */
    private static final int TS_ID = 0x7f0f0003;

    private static final String[] FAVORITES = {
            "org.smarttube.stable", "de.example.mausapp", "io.github.artmode",
            "com.netflix.ninja", "com.amazon.firebat", "com.disney.disneyplus",
            "com.zdf.android.mediathek", "tv.arte.plus7", "com.plexapp.android",
            "de.swr.ard.avp.mobile.android.amazon",
    };

    private FrameLayout root;
    private LinearLayout appGrid;
    private TextView clockBig, dateLine, status;
    private WeatherPanel weatherPanel;
    private StocksPanel stocksPanel;
    private NewsTile newsTile;
    private TextView liveTxt, newsTxt;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private float dp;
    private boolean appsBuilt = false;

    private final java.text.SimpleDateFormat timeFmt =
            new java.text.SimpleDateFormat("HH:mm", Locale.GERMANY);
    private final java.text.SimpleDateFormat dateFmt =
            new java.text.SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY);

    private final Runnable screensaver = new Runnable() {
        public void run() { startArt(); }
    };

    private final Runnable clockTick = new Runnable() {
        public void run() {
            updateClock();
            ui.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;

        // Layout-Basis fuer alle festen dp-Werte: 960 x 540 dp.
        // Auf dem Fire TV Stick (1920x1080 @ 320 dpi) ist das genau die
        // Anzeigeflaeche; auf anderen Dichten wird der gesamte Seiteninhalt
        // proportional mitskaliert, damit die Rechnung immer aufgeht und
        // keine Leerraeume entstehen.
        final int W = 960, H = 540;
        final float realDp = dp;
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        final float wpx = dm.widthPixels, hpx = dm.heightPixels;
        final float s = Math.min(wpx / (W * realDp), hpx / (H * realDp));
        this.dp = realDp * s;

        buildUi();

        if (s < 0.999f || s > 1.001f) {
            root.post(new Runnable() {
                public void run() {
                    root.setScaleX(s);
                    root.setScaleY(s);
                    root.setPivotX(0f);
                    root.setPivotY(0f);
                    FrameLayout.LayoutParams lp =
                            (FrameLayout.LayoutParams) root.getLayoutParams();
                    lp.width = Math.round(wpx / s);
                    lp.height = Math.round(hpx / s);
                    lp.gravity = Gravity.LEFT | Gravity.TOP;
                    root.setLayoutParams(lp);
                }
            });
        }

        // Selbstheilung: prueft im Hintergrund, ob der Accessibility-Dienst noch
        // eingetragen ist (nach Update/Boot schaltet Fire OS ihn ab) und traegt
        // ihn wieder ein. Laeuft 0,5 s NACH dem Aufbau, damit der Start nicht bremst.
        root.postDelayed(new Runnable() {
            public void run() { ServiceGuard.ensure(HomeActivity.this); }
        }, 500);
    }

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        root.post(new Runnable() { public void run() { HomeService.appShown(); } });
        // Bei JEDEM Vordergrund-Wechsel pruefen (nicht nur beim Start): nach
        // einem App-Update bleibt die Activity am Leben, onCreate laeuft dann
        // nicht erneut. Der Guard ist dank 60-s-Sperre praktisch kostenlos.
        root.post(new Runnable() {
            public void run() { ServiceGuard.ensure(HomeActivity.this); }
        });

        updateClock();
        ui.removeCallbacks(clockTick);
        ui.post(clockTick);
        // Ort: gespeichert -> sofort nutzen; sonst einmal orten, dann laden
        Place.LastContext.set(this);
        Place.Info place = Place.load(this);
        Weather.usePlace(place);
        Place.locateIfNeeded(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        Weather.usePlace(Place.load(HomeActivity.this));
                        Weather.refresh(HomeActivity.this, new Runnable() {
                            public void run() {
                                ui.post(new Runnable() {
                                    public void run() { updateClock(); }
                                });
                            }
                        });
                    }
                });
            }
        });

        Weather.refresh(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() { public void run() { updateClock(); } });
            }
        });
        stocksPanel.show(Stocks.cached(this));
        Stocks.refresh(this, new Runnable() {
            public void run() {
                final java.util.List<Stocks.Quote> fresh = Stocks.cached(HomeActivity.this);
                ui.post(new Runnable() {
                    public void run() { stocksPanel.show(fresh); }
                });
            }
        });

        newsTile.show(SzNews.cached(this));
        SzNews.refresh(this, new Runnable() {
            public void run() {
                final java.util.List<SzNews.Item> fresh = SzNews.cached(HomeActivity.this);
                ui.post(new Runnable() {
                    public void run() { newsTile.show(fresh); }
                });
            }
        });

        if (!appsBuilt) {
            appsBuilt = true;
            loadApps();
        }
        // Startfokus auf die Live-TV-Kachel, damit die Navigation eine Basis hat.
        // Auch nach der Rueckkehr aus einer anderen Activity (onResume) noetig,
        // sonst steht der Fokus nach BACK auf keinem Element.
        root.post(new Runnable() {
            public void run() {
                android.view.View v = root.findViewById(KACHEL_ID);
                if (v != null) v.requestFocus();
            }
        });
        resetScreensaver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(screensaver);
        ui.removeCallbacks(clockTick);
    }

    private void updateClock() {
        if (clockBig == null) return;
        java.util.Date now = new java.util.Date();
        clockBig.setText(timeFmt.format(now));
        String d = dateFmt.format(now);
        if (d.length() > 0) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        dateLine.setText(d);
        if (weatherPanel != null) weatherPanel.show(Weather.cached(this));
    }

    private void resetScreensaver() {
        ui.removeCallbacks(screensaver);
        ui.postDelayed(screensaver, SCREENSAVER_MS);
    }

    private void startArt() {
        try {
            Intent i = new Intent();
            i.setClassName(ART_PKG, ART_CLS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException ignored) { }
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

    // ================= Layout-Rechnung (Basis 960 x 540 dp) =================
    // Fire TV: 1920 x 1080 px @ 320 dpi -> density 2.0 -> 960 x 540 dp.
    //
    // SENKRECHT (Summe muss 540 ergeben)
    //   Seiten-Padding oben                     16
    //   Kopf (Uhr 32 sp, Datum 13 sp)           50
    //   Abstand                                  4
    //   Obere Zeile                             188   <- fest
    //   Abstand                                 12
    //   Untere Zeile                            258   <- Gewicht 1, fuellt Rest
    //   Seiten-Padding unten                    12
    //   ----------------------------------------------
    //                                           540   exakt
    //   Untere Zeile (258 dp): Kopf "Apps" 20 + Abstand 6 + Raster.
    //   Raster: 4 Kacheln je 68 dp + 3 * 10 dp Abstand = 302 dp Breite;
    //   in der Hoehe 4 Reihen je 68 dp + 3 * 10 dp = 302 dp.
    //   20 + 6 + 302 = 328 dp > 258 dp -> die vierte Reihe liegt unter der
    //   Kante und wird per D-Pad durch die ScrollView nachgezogen.
    //
    // WAAGERECHT (Innenbreite 864 dp = 960 - 48 - 48, Abstand immer 8 dp)
    //   Obere Zeile:
    //     Buttons-Spalte     68   (exakt eine App-Kachel: 52 Icon + 2*8)
    //     Abstand             8
    //     Aktien            340
    //     Abstand             8
    //     Wetter            440   (Gewicht 1 -> fuellt den Rest)
    //     Summe           68+8+340+8+440 = 864
    //     Wetterbreite / Innenbreite = 440/864 = 50,9 % -> Anforderung
    //     "Wetter 50 %" erfuellt; die Kachel beginnt bei 48+68+8+340+8 = 472 dp,
    //     also links von der Bildmitte (480 dp) - sie ragt wie gewuenscht ueber
    //     die halbe Bildbreite hinaus.
    //   Untere Zeile:
    //     Apps              302   (4 Kacheln * 68 + 3 * 10 Abstand, fest)
    //     Abstand             8
    //     News              554   (Gewicht 1 -> fuellt den Rest, volle Hoehe)
    //   Damit gibt es keine schwarzen Balken mehr: jede Zeile ist
    //   MATCH_PARENT breit, die News-Kachel nimmt den gesamten Rest unten.
    //
    // FOKUS (BUG: mehrere Kacheln gleichzeitig orange)
    //   Ursache: Frueher lag ein fokussierter Kachel-Zustand im Hintergrund
    //   eines Views, der beim Recycling dieselbe Flaeche mehrfach ansprach,
    //   und das Raster lief ueber seine Spalte hinaus (Gewicht 1, 0-Breite).
    //   Korrektur: feste Kachelbreite, jede Kachel setzt ihren Hintergrund
    //   IMMER beim Fokuswechsel (has ? Rahmen : Ui.card) - nie nur bei
    //   has==true -, und es gibt keine geteilten Drawables mehr.
    // =========================================================================

    private static final int PAGE_L = 48, PAGE_R = 48, PAGE_T = 16, PAGE_B = 12;
    private static final int GAP = 8;          // Abstand zwischen Kacheln
    private static final int HEAD_H = 50;
    private static final int GAP_HEAD = 4;
    private static final int TOP_H = 128;
    private static final int GAP_MID = 12;
    private static final int BTN_W = 68;       // <<< so breit wie eine App-Kachel
    private static final int STOCK_W = 240;
    private static final int CELL_GAP = 10;

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(PAGE_L), dp(PAGE_T), dp(PAGE_R), dp(PAGE_B));

        // ---- Kopf: Uhr links, Datum rechts (feste Hoehe) ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        clockBig = new TextView(this);
        clockBig.setTextColor(Ui.TEXT);
        clockBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        clockBig.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clockBig.setIncludeFontPadding(false);
        head.addView(clockBig, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        dateLine = new TextView(this);
        dateLine.setTextColor(Ui.TEXT_DIM);
        dateLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dateLine.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        head.addView(dateLine);

        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(HEAD_H));
        headLp.bottomMargin = dp(GAP_HEAD);
        page.addView(head, headLp);

        // ---- Obere Zeile: Buttons links, Aktien, Wetter rechts ----
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);

        // (1) Buttons: je 68 dp breit (wie eine App-Kachel), untereinander
        LinearLayout btnCol = new LinearLayout(this);
        btnCol.setOrientation(LinearLayout.VERTICAL);
        int btnH = (TOP_H - GAP) / 2;

        View liveBtn = smallButton("Live-TV", "Sender", false, new Runnable() {
            public void run() {
                ui.removeCallbacks(screensaver);
                HomeService.pauseFor(10 * 60 * 1000);
                startActivity(new Intent(HomeActivity.this, ChannelListActivity.class));
            }
        });
        liveBtn.setId(KACHEL_ID);
        LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(
                dp(BTN_W), dp(btnH));
        lbp.bottomMargin = dp(GAP);
        btnCol.addView(liveBtn, lbp);

        View tsBtn = smallButton("Tagesschau", "100 Sek.", true, new Runnable() {
            public void run() {
                ui.removeCallbacks(screensaver);
                HomeService.pauseFor(10 * 60 * 1000);
                startActivity(new Intent(HomeActivity.this, TagesschauActivity.class));
            }
        });
        tsBtn.setId(TS_ID);
        btnCol.addView(tsBtn, new LinearLayout.LayoutParams(
                dp(BTN_W), dp(btnH)));

        LinearLayout.LayoutParams bclp = new LinearLayout.LayoutParams(
                dp(BTN_W), dp(TOP_H));
        bclp.rightMargin = dp(GAP);
        topRow.addView(btnCol, bclp);

        // (2) Aktien: feste Breite, volle Hoehe - gross und luftig
        stocksPanel = new StocksPanel(this);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                dp(STOCK_W), dp(TOP_H));
        stlp.rightMargin = dp(GAP);
        topRow.addView(stocksPanel, stlp);

        // (3) Wetter: fuellt den Rest der Zeile (= rechte Bildhaelfte)
        weatherPanel = new WeatherPanel(this);
        topRow.addView(weatherPanel, new LinearLayout.LayoutParams(
                0, dp(TOP_H), 1f));

        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = dp(GAP_MID);
        page.addView(topRow, topLp);

        // ---- Untere Zeile: links Apps, rechts News (fuellt den Rest) ----
        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.HORIZONTAL);

        // LINKS: Apps in eigener Spalte, Breite = was das 4er-Raster braucht
        LinearLayout appsCol = new LinearLayout(this);
        appsCol.setOrientation(LinearLayout.VERTICAL);

        int appColW = 4 * (APP_ICON + 2 * APP_PAD) + 3 * CELL_GAP;

        LinearLayout appHead = new LinearLayout(this);
        appHead.setOrientation(LinearLayout.HORIZONTAL);
        appHead.setGravity(Gravity.CENTER_VERTICAL);
        appHead.setPadding(0, 0, 0, dp(6));

        TextView al = new TextView(this);
        al.setText("Apps");
        al.setTextColor(Ui.TEXT_DIM);
        al.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        al.setTypeface(Typeface.DEFAULT_BOLD);
        al.setLetterSpacing(0.18f);
        appHead.addView(al, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextColor(Ui.TEXT_DIM);
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        gear.setPadding(dp(10), dp(3), dp(10), dp(3));
        gear.setFocusable(true);
        gear.setFocusableInTouchMode(true);
        gear.setBackground(Ui.card(dp, 7));
        gear.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                // Grundzustand ohne Ring (siehe buildAppCard) - nur die
                // tatsaechlich fokussierte Kachel bekommt den Akzent.
                v.setBackground(Ui.focusRing(dp, has ? Ui.CARD_FOCUS : Ui.CARD,
                        has ? Ui.ACCENT : Ui.CARD, 7));
                ((TextView) v).setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT_DIM);
            }
        });
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ui.removeCallbacks(screensaver);
                HomeService.pauseFor(10 * 60 * 1000);
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        appHead.addView(gear);

        appsCol.addView(appHead);

        appGrid = new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView appScroll = new android.widget.ScrollView(this);
        appScroll.setVerticalScrollBarEnabled(false);
        appScroll.addView(appGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        appsCol.addView(appScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout.LayoutParams aclp = new LinearLayout.LayoutParams(
                dp(appColW), ViewGroup.LayoutParams.MATCH_PARENT);
        aclp.rightMargin = dp(GAP);
        lower.addView(appsCol, aclp);

        // RECHTS: News, fuellt den Rest inkl. Hoehe
        newsTile = new NewsTile(this);
        newsTile.setId(NEWS_TILE_ID);
        lower.addView(newsTile, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        page.addView(lower, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));


        status = new TextView(this);
        status.setText("Einen Moment ...");
        status.setTextColor(Ui.TEXT_DIM);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER;
        root.addView(status, sp);

        setContentView(root);
    }

    /**
     * Schmale Schaltflaeche fuer die obere Zeile: links ein Farbstreifen in
     * der Sender-/Akzentfarbe, rechts Titel und Untertitel.
     * Bewusst kompakt - diese Buttons sind reine Sprungbretter.
     */
    private View smallButton(final String titel, final String sub, final boolean accent,
                             final Runnable action) {
        // Format bewusst wie eine App-Kachel: 68 dp breit, quadratisch,
        // Farbbalken OBEN in der Sender-/Akzentfarbe, darunter Titel und
        // Untertitel mittig umbrechend. Ein Farbbalken links plus Text
        // passt bei 68 dp nicht - dann wird der Titel abgeschnitten.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(5), dp(9), dp(5), dp(9));
        card.setBackground(Ui.card(dp, 10));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        // Ohne clickable reagiert die Kachel nicht auf OK - Focusable allein
        // reicht bei programmatisch gebauten Views nicht.
        card.setClickable(true);

        View stripe = new View(this);
        stripe.setBackground(Ui.round(accent ? Ui.TEXT_ON_FOCUS : Ui.CARD_FOCUS, dp, 2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(18), dp(4));
        slp.bottomMargin = dp(7);
        card.addView(stripe, slp);

        final TextView t = new TextView(this);
        t.setText(titel);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        // Akzent-Kachel (Tagesschau) traegt ihre Farbe IMMER, alle anderen
        // starten neutral. Ring immer neu setzen (BUG: liegengebliebener Rahmen).
        card.setBackground(accent ? Ui.focusRing(dp, Ui.CARD_FOCUS, Ui.CARD_FOCUS, 18)
                                  : Ui.card(dp, 10));
        t.setTextColor(accent ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
        t.setMaxLines(2);
        t.setLineSpacing(dp(1), 1f);
        card.addView(t);

        final TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(accent ? Color.rgb(80, 56, 24) : Ui.TEXT_DIM);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        s.setIncludeFontPadding(false);
        s.setGravity(Gravity.CENTER);
        s.setSingleLine(true);
        s.setPadding(0, dp(3), 0, 0);
        card.addView(s);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (accent) {
                    v.setBackground(Ui.focusRing(dp, Ui.CARD_FOCUS,
                            has ? Color.WHITE : Ui.CARD_FOCUS, 18));
                } else {
                    v.setBackground(Ui.focusRing(dp, has ? Ui.CARD_FOCUS : Ui.CARD,
                            has ? Ui.ACCENT : Ui.CARD, 10));
                    t.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
                    s.setTextColor(has ? Color.rgb(80, 56, 24) : Ui.TEXT_DIM);
                }
                v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                        .setDuration(100).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        return card;
    }

    /** Grosse tvOS-Kachel mit duenner Schrift. */
    private View bigTile(final String title, String sub, boolean accent, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(24), dp(18), dp(24), dp(18));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackground(Ui.card(dp, 14));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Ui.TEXT_DIM);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s.setPadding(0, dp(3), 0, 0);
        card.addView(s);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(Ui.round(has ? Ui.CARD_FOCUS : Ui.CARD, dp, 14));
                LinearLayout c = (LinearLayout) v;
                if (c.getChildCount() > 0) ((TextView) c.getChildAt(0))
                        .setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
                if (c.getChildCount() > 1) ((TextView) c.getChildAt(1))
                        .setTextColor(has ? Color.rgb(70, 52, 28) : Ui.TEXT_DIM);
                v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                        .setDuration(110).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        // Nach oben geht der Fokus in die SZ-Leiste, nicht ins Leere
        card.setNextFocusUpId(NEWS_TILE_ID);
        return card;
    }

    // ---------------- Apps ----------------

    private void loadApps() {
        new Thread(new Runnable() {
            public void run() {
                final List<AppEntry> apps = collectApps();
                android.util.Log.i("MAUS", "Apps gefunden: " + apps.size());
                ui.post(new Runnable() {
                    public void run() {
                        renderApps(apps);
                        if (apps.size() > 0) {
                            status.setVisibility(View.GONE);
                        } else {
                            status.setText("Keine Apps gefunden");
                        }
                    }
                });
            }
        }).start();
    }

    private List<AppEntry> collectApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<String>();
        Map<String, AppEntry> map = new LinkedHashMap<String, AppEntry>();

        List<ResolveInfo> ris = new ArrayList<ResolveInfo>();
        ris.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        ris.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));

        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (pkg.startsWith("com.amazon")) continue;
            if (!seen.add(pkg)) continue;

            AppEntry e = new AppEntry();
            e.pkg = pkg;
            e.label = ri.loadLabel(pm).toString();
            e.icon = iconFor(pkg, ri, pm);
            e.act = new android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            map.put(pkg, e);
        }

        List<AppEntry> list = new ArrayList<AppEntry>(map.values());
        Collections.sort(list, new Comparator<AppEntry>() {
            public int compare(AppEntry x, AppEntry y) {
                int ix = fav(x.pkg), iy = fav(y.pkg);
                if (ix != iy) return ix - iy;
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return list;
    }

    private int fav(String pkg) {
        for (int i = 0; i < FAVORITES.length; i++) if (FAVORITES[i].equals(pkg)) return i;
        return FAVORITES.length;
    }

    /**
     * Icon fuer eine App waehlen.
     *
     * Warum eigene Logos: viele Streaming-Apps liefern im Fire-TV-Banner ein
     * Werbebild statt ihres Logos (Verlauf, Serienfoto, Cast). Auf dem
     * Startbildschirm ist so eine Kachel nicht wiederzuerkennen. Fuer die
     * wichtigsten Dienste liegen deshalb die offiziellen Wortmarken als PNG
     * in res/drawable-nodpi. Alles andere nimmt weiter sein eigenes Icon.
     */
    private Drawable iconFor(String pkg, ResolveInfo ri, PackageManager pm) {
        int res = logoFor(pkg);
        if (res != 0) {
            try {
                Drawable d = getResources().getDrawable(res, getTheme());
                if (d != null) return d;
            } catch (RuntimeException ignored) { }
        }
        return ri.loadIcon(pm);
    }

    private int logoFor(String pkg) {
        if (pkg.equals("com.netflix.ninja")) return R.drawable.logo_netflix;
        if (pkg.equals("com.disney.disneyplus")) return R.drawable.logo_disney;
        if (pkg.equals("com.zdf.android.mediathek")) return R.drawable.logo_zdf;
        if (pkg.equals("tv.arte.plus7")) return R.drawable.logo_arte;
        if (pkg.equals("com.plexapp.android")) return R.drawable.logo_plex;
        if (pkg.startsWith("de.swr.ard") || pkg.startsWith("de.ard")) return R.drawable.logo_ard;
        if (pkg.equals("de.roloff.maus") || pkg.equals("de.example.mausapp")) return R.drawable.logo_maus;
        return 0;
    }

    private void renderApps(List<AppEntry> apps) {
        appGrid.removeAllViews();
        final int perRow = 4;
        final int cardW = dp(APP_ICON + 2 * APP_PAD);   // 68 dp
        final int gap = dp(CELL_GAP);                   // 10 dp
        LinearLayout row = null;

        for (int i = 0; i < apps.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                // Feste Breite = 4 Kacheln + 3 Abstaende. Frueher stand hier
                // MATCH_PARENT + weightSum: die Reihe lief damit breiter als
                // die Spalte und Kacheln unterschiedlicher Reihen lagen
                // uebereinander - so entstanden mehrere orange Rahmen.
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                appGrid.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    cardW, cardW);
            if (row.getChildCount() < perRow - 1) lp.rightMargin = gap;
            if (i < apps.size() - perRow) lp.bottomMargin = gap;
            row.addView(buildAppCard(apps.get(i)), lp);
        }

        // Fehlende Zellen in der letzten Reihe mit Blindplatz fuellen, damit
        // die vorhandenen Kacheln ihre Position behalten.
        if (row != null) {
            int missing = perRow - row.getChildCount();
            for (int i = 0; i < missing; i++) {
                View gapV = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        cardW, cardW);
                if (row.getChildCount() < perRow - 1) lp.rightMargin = gap;
                row.addView(gapV, lp);
            }
        }
    }

    private View buildAppCard(final AppEntry e) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(APP_PAD), dp(APP_PAD), dp(APP_PAD), dp(APP_PAD));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackground(Ui.card(dp, 8));

        // Nur das Symbol, kein Name darunter - wie auf Android.
        // Die Zuordnung erkennt man am Logo; Text kostet nur Platz, den das
        // Raster besser nutzt.
        ImageView ic = new ImageView(this);
        if (e.icon != null) {
            ic.setImageDrawable(e.icon);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        card.addView(ic, new LinearLayout.LayoutParams(dp(APP_ICON), dp(APP_ICON)));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                // 3-dp-Rahmen nach Android-TV-Guideline - bei reinen Icons
                // ist der Rahmen die einzige Fokusanzeige, die traegt.
                // Grundzustand = Ui.card (OHNE Ring): sonst bleibt ein
                // einmal gesetzter Rahmen am View haengen und mehrere
                // Kacheln leuchten gleichzeitig orange.
                v.setBackground(Ui.focusRing(dp, has ? Ui.CARD_FOCUS : Ui.CARD,
                        has ? Ui.ACCENT : Ui.CARD, 8));
                v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f)
                        .setDuration(90).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { launch(e); }
        });
        return card;
    }

    private void launch(AppEntry e) {
        ui.removeCallbacks(screensaver);
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(e.act);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException ex) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            } catch (RuntimeException ignored) { }
        }
    }

    private void openSettings() {
        HomeService.pauseFor(10 * 60 * 1000);
        try {
            Intent i = new Intent("android.settings.SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (RuntimeException e) {
            try {
                Intent i = new Intent();
                i.setComponent(new android.content.ComponentName("com.amazon.tv.settings.v2",
                        "com.amazon.tv.settings.v2.tv.device.DeviceActivity"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (RuntimeException ignored) { }
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        resetScreensaver();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (appGrid != null && appGrid.getChildCount() > 0) {
                LinearLayout first = (LinearLayout) appGrid.getChildAt(0);
                if (first.getChildCount() > 0) first.getChildAt(0).requestFocus();
            }
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
    }

    static class AppEntry {
        String pkg, label;
        Drawable icon;
        android.content.ComponentName act;
    }
}
