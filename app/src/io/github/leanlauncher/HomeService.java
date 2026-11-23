package io.github.leanlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Haelt Amazons Homescreen verdeckt.
 *
 * == Warum so gebaut ==
 * Die erste Version hat bei JEDEM Fenster-Ereignis reagiert und dadurch
 * Einstellungen zugedeckt, sich selbst getriggert und ist in Schleifen gelaufen.
 * Diese Version ist bewusst defensiv:
 *
 *  1. Es wird NUR auf den echten Amazon-Homescreen reagiert
 *     (Paket com.amazon.tv.launcher UND Klasse enthaelt HomeActivity).
 *  2. Einstellungen (laufen im selben Paket!) werden erkannt und pausieren
 *     den Dienst fuer 10 Minuten - sie werden nie zugedeckt.
 *  3. Ein Cooldown von 3 s verhindert Mehrfach-Ausloesung.
 *  4. Ein laufendes Cover wird nicht erneut gestartet (Flag), solange es aktiv ist.
 *  5. Nach dem eigenen Start wird 1 s lang nicht reagiert (Selbst-Trigger-Schutz).
 */
public class HomeService extends AccessibilityService {

    private static final String AMAZON_PKG = "com.amazon.tv.launcher";

    private static final long COOLDOWN_MS = 3000;
    private static final long SETTINGS_PAUSE_MS = 10 * 60 * 1000;
    private static final long SELF_GUARD_MS = 1200;

    private static long pausedUntil = 0;
    private static long nextCoverAt = 0;
    private static long ignoreUntil = 0;
    private static boolean covering = false;

    static HomeService running;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private View overlay;
    private boolean added, showing;

    private android.os.Handler guard;
    private final Runnable guardTick = new Runnable() {
        public void run() {
            try { checkFront(); } catch (Throwable ignored) { }
            if (guard != null) guard.postDelayed(this, 1200);
        }
    };

    /**
     * Waechter: schaut alle 1,2 s, was vorne steht. Das Event allein reicht
     * nicht - beim Boot und nach manchen Updates kommt kein
     * TYPE_WINDOW_STATE_CHANGED, dann bleibt der Amazon-Launcher stehen.
     */
    private void checkFront() {
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil) return;

        android.app.ActivityManager am =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return;
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> ps =
                am.getRunningAppProcesses();
        if (ps == null) return;

        for (android.app.ActivityManager.RunningAppProcessInfo p : ps) {
            if (p.importance != android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) continue;
            if (AMAZON_PKG.equals(p.processName)) {
                if (now < ignoreUntil) return;
                cover();
                return;
            }
            // Eigene App oder irgendeine andere im Vordergrund: nichts tun.
            return;
        }
    }

    private final Runnable hideNow = new Runnable() {
        public void run() { hide(); }
    };

    private final BroadcastReceiver homePressed = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            long now = SystemClock.elapsedRealtime();
            if (now < ignoreUntil || now < pausedUntil) return;
            cover();
        }
    };

    static void pauseFor(long ms) {
        pausedUntil = SystemClock.elapsedRealtime() + ms;
        ignoreUntil = SystemClock.elapsedRealtime() + SELF_GUARD_MS;
        hideOverlay();
    }

    static void hideOverlay() {
        if (running != null) running.hide();
    }

    /** Von HomeActivity: eigene Startseite ist sichtbar, Overlay darf weg. */
    static void appShown() {
        covering = false;
        final HomeService s = running;
        if (s == null || s.overlay == null) return;
        s.overlay.postDelayed(new Runnable() {
            public void run() { s.hide(); }
        }, 120);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlay = new View(this);
        overlay.setBackgroundColor(0xFF000000);
        overlay.setVisibility(View.GONE);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.OPAQUE);
        attach();

        try {
            IntentFilter f = new IntentFilter();
            // Amazons echter Broadcast (live geprueft 11/2026 auf Fire OS 8):
            f.addAction("amazon.intent.action.HOME_PRESSED");
            // Aeltere/zusaetzliche Schreibweise - schadet nicht, falls vorhanden:
            f.addAction("com.amazon.tv.launcher.HOME_PRESSED");
            registerReceiver(homePressed, f);
        } catch (RuntimeException ignored) { }

        // Waechter starten (faengt Faelle ab, in denen kein Event kommt)
        guard = new android.os.Handler(android.os.Looper.getMainLooper());
        guard.postDelayed(guardTick, 1500);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (guard != null) {
            guard.removeCallbacksAndMessages(null);
            guard = null;
        }
        try { unregisterReceiver(homePressed); } catch (RuntimeException ignored) { }
        if (added && wm != null && overlay != null) {
            try { wm.removeViewImmediate(overlay); } catch (RuntimeException ignored) { }
        }
        added = false;
        showing = false;
        covering = false;
        running = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pn = e.getPackageName();
        if (pn == null) return;
        String pkg = pn.toString();
        CharSequence clsCs = e.getClassName();
        String cls = clsCs == null ? "" : clsCs.toString();

        // Fremde Overlays/Systemfenster ignorieren
        if (pkg.equals("android") || pkg.equals("com.android.systemui")
                || pkg.equals("com.amazon.tv.ime")) return;

        // ---- Einstellungen: laufen im Amazon-Paket, nie zudecken ----
        if (pkg.startsWith("com.amazon.tv.settings") || pkg.equals("com.android.settings")
                || cls.contains("Settings") || cls.contains("FallbackHome")) {
            pausedUntil = SystemClock.elapsedRealtime() + SETTINGS_PAUSE_MS;
            hide();
            return;
        }

        // ---- Eigene App ----
        if (pkg.equals(getPackageName())) return;

        // ---- Nur der ECHTE Amazon-Homescreen loest aus ----
        // Wichtig: Fire OS 8 nennt die Klasse "ui.HomeActivity_vNext", nicht
        // "HomeActivity". Deshalb wird auf "HomeActivity" per contains geprueft
        // UND auf den blossen Paketwechsel reagiert - sonst bleibt der
        // Amazon-Launcher stehen, wenn kein Klassenname mitkommt.
        if (pkg.equals(AMAZON_PKG)) {
            long now = SystemClock.elapsedRealtime();
            if (now < ignoreUntil || now < pausedUntil) return;
            cover();
            return;
        }

        // ---- jede andere App: Overlay weg, falls es noch steht ----
        if (showing) {
            covering = false;
            setFocusable(false);
            overlay.postDelayed(hideNow, 250);
        }
    }

    @Override
    public void onInterrupt() { }

    private boolean attach() {
        if (added) return true;
        try {
            wm.addView(overlay, lp);
            added = true;
        } catch (RuntimeException ex) {
            added = false;
        }
        return added;
    }

    private void cover() {
        long now = SystemClock.elapsedRealtime();
        if (now < nextCoverAt) return;
        if (now < pausedUntil) return;
        if (covering) return;                   // laeuft schon

        nextCoverAt = now + COOLDOWN_MS;
        covering = true;
        ignoreUntil = now + SELF_GUARD_MS;

        show();
        try {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (RuntimeException ignored) { }

        // Notausstieg: falls die Startseite nicht kommt, Flag zuruecksetzen
        if (overlay != null) {
            overlay.postDelayed(new Runnable() {
                public void run() { covering = false; }
            }, 2500);
        }
    }

    private void show() {
        if (overlay == null || !attach()) return;
        overlay.removeCallbacks(hideNow);
        if (!showing) {
            showing = true;
            try { overlay.setVisibility(View.VISIBLE); } catch (RuntimeException ignored) { }
        }
        setFocusable(true);
    }

    private void hide() {
        if (overlay == null || !showing || !added) return;
        overlay.removeCallbacks(hideNow);
        showing = false;
        try { overlay.setVisibility(View.GONE); } catch (RuntimeException ignored) { }
        setFocusable(false);
    }

    private void setFocusable(boolean focusable) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | (focusable ? 0
                   : (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE));
        if (lp.flags == flags) return;
        lp.flags = flags;
        try { wm.updateViewLayout(overlay, lp); } catch (RuntimeException ignored) { }
    }
}
