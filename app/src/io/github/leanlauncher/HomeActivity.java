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

    private static final String[] FAVORITES = {
            "org.smarttube.stable", "de.example.mausapp", "io.github.artmode",
            "com.netflix.ninja", "com.amazon.firebat", "com.disney.disneyplus",
            "com.zdf.android.mediathek", "tv.arte.plus7", "com.plexapp.android",
            "de.swr.ard.avp.mobile.android.amazon",
    };

    private FrameLayout root;
    private LinearLayout appGrid;
    private TextView clockBig, dateLine, weatherLine, status;
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
        buildUi();
    }

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        root.post(new Runnable() { public void run() { HomeService.appShown(); } });

        updateClock();
        ui.removeCallbacks(clockTick);
        ui.post(clockTick);
        Weather.refresh(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() { public void run() { updateClock(); } });
            }
        });

        if (!appsBuilt) {
            appsBuilt = true;
            loadApps();
        }
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
        String w = Weather.cached(this);
        weatherLine.setText(w.length() > 0 ? Weather.CITY + "  " + w : "");
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

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(30), dp(56), dp(26));

        // ---- Kopf: Uhr gross und duenn, rechts Datum + Wetter ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(0, 0, 0, dp(22));

        clockBig = new TextView(this);
        clockBig.setTextColor(Ui.TEXT);
        clockBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        clockBig.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clockBig.setIncludeFontPadding(false);
        head.addView(clockBig, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.BOTTOM);

        dateLine = new TextView(this);
        dateLine.setTextColor(Ui.TEXT_DIM);
        dateLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dateLine.setGravity(Gravity.RIGHT);
        meta.addView(dateLine);

        weatherLine = new TextView(this);
        weatherLine.setTextColor(Ui.TEXT_DIM);
        weatherLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        weatherLine.setGravity(Gravity.RIGHT);
        meta.addView(weatherLine);

        head.addView(meta);
        page.addView(head);

        // ---- Zwei Hauptkacheln ----
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);

        View live = bigTile("Live-TV", "11 Sender · Programm & Vorschau", true,
                new Runnable() { public void run() {
                    ui.removeCallbacks(screensaver);
                    startActivity(new Intent(HomeActivity.this, ChannelListActivity.class));
                }});
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t1.rightMargin = dp(12);
        tiles.addView(live, t1);

        View news = bigTile("Nachrichten", "Tagesschau · DLF · Tagesthemen", false,
                new Runnable() { public void run() {
                    ui.removeCallbacks(screensaver);
                    startActivity(new Intent(HomeActivity.this, NewsActivity.class));
                }});
        tiles.addView(news, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        page.addView(tiles);

        // ---- Apps ----
        LinearLayout appHead = new LinearLayout(this);
        appHead.setOrientation(LinearLayout.HORIZONTAL);
        appHead.setGravity(Gravity.CENTER_VERTICAL);
        appHead.setPadding(0, dp(22), 0, dp(9));

        TextView al = new TextView(this);
        al.setText("Apps");
        al.setTextColor(Ui.TEXT_DIM);
        al.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        al.setTypeface(Typeface.DEFAULT_BOLD);
        al.setLetterSpacing(0.18f);
        appHead.addView(al, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView gear = new TextView(this);
        gear.setText("⚙ Einstellungen");
        gear.setTextColor(Ui.TEXT_DIM);
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        gear.setPadding(dp(12), dp(5), dp(12), dp(5));
        gear.setFocusable(true);
        gear.setFocusableInTouchMode(true);
        gear.setBackground(Ui.card(dp, 7));
        gear.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(Ui.round(has ? Ui.CARD_FOCUS : Ui.CARD, dp, 7));
                ((TextView) v).setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT_DIM);
            }
        });
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        appHead.addView(gear);

        page.addView(appHead);

        appGrid = new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        page.addView(appGrid);

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
            e.icon = ri.loadIcon(pm);
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

    private void renderApps(List<AppEntry> apps) {
        appGrid.removeAllViews();
        final int perRow = 8;
        LinearLayout row = null;

        for (int i = 0; i < apps.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                appGrid.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(5);
            lp.bottomMargin = dp(5);
            row.addView(buildAppCard(apps.get(i)), lp);
        }
    }

    private View buildAppCard(final AppEntry e) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(3), dp(7), dp(3), dp(5));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackground(Ui.card(dp, 10));

        ImageView ic = new ImageView(this);
        if (e.icon != null) ic.setImageDrawable(e.icon);
        card.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView t = new TextView(this);
        t.setText(e.label);
        t.setTextColor(Ui.TEXT_DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setPadding(0, dp(4), 0, 0);
        card.addView(t);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(Ui.round(has ? Ui.CARD_FOCUS : Ui.CARD, dp, 10));
                LinearLayout c = (LinearLayout) v;
                if (c.getChildCount() > 1) ((TextView) c.getChildAt(1))
                        .setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT_DIM);
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
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
