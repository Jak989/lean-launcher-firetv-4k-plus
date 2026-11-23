package io.github.leanlauncher;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Spielt die neueste "tagesschau in 100 Sekunden" ab.
 *
 * Kein Browser, kein Intent-Dialog: die App holt die MP4-Adresse direkt aus
 * der Tagesschau-API und startet den VideoView. 16:9 formatfuellend wie bei
 * den Livestreams.
 *
 * Bedienung: OK Pause/Weiter, links/rechts 15 s springen, BACK zurueck.
 */
public class TagesschauActivity extends Activity {

    private VideoView video;
    private FrameLayout box, root;
    private TextView title, meta, hint;
    private ProgressBar spinner;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean started = false;
    private final Runnable hideHint = new Runnable() {
        public void run() { if (hint != null) hint.setVisibility(View.GONE); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        box = new FrameLayout(this);
        box.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(0, 0);
        blp.gravity = Gravity.CENTER;
        root.addView(box, blp);

        video = new VideoView(this);
        video.setKeepScreenOn(true);
        box.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Kopfzeile: Titel + Datum
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setShadowLayer(8f, 0f, 2f, 0xD0000000);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP | Gravity.LEFT;
        tlp.leftMargin = dp(56);
        tlp.topMargin = dp(34);
        root.addView(title, tlp);

        meta = new TextView(this);
        meta.setTextColor(Color.rgb(210, 210, 220));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setShadowLayer(8f, 0f, 2f, 0xD0000000);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.gravity = Gravity.TOP | Gravity.LEFT;
        mlp.leftMargin = dp(56);
        mlp.topMargin = dp(60);
        root.addView(meta, mlp);

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(48), dp(48));
        slp.gravity = Gravity.CENTER;
        root.addView(spinner, slp);

        hint = new TextView(this);
        hint.setText("OK  =  Pause        ›/‹  =  15 s springen        BACK  =  zurück");
        hint.setTextColor(Color.rgb(190, 190, 200));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setShadowLayer(6f, 0f, 1f, 0xD0000000);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(28);
        root.addView(hint, hlp);

        setContentView(root);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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

    /** Video holen (Cache oder frisch) und starten. */
    private void start() {
        Tagesschau.Video v = Tagesschau.cached(this);
        if (v != null && v.valid()) {
            play(v);
        } else {
            title.setText("tagesschau in 100 Sekunden");
            meta.setText("wird geladen …");
        }

        Tagesschau.refresh(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        Tagesschau.Video fresh = Tagesschau.cached(TagesschauActivity.this);
                        if (fresh != null && fresh.valid() && !started) play(fresh);
                        else if (fresh == null && !started) {
                            spinner.setVisibility(View.GONE);
                            meta.setText("Video gerade nicht abrufbar.");
                        }
                    }
                });
            }
        });
    }

    private void play(Tagesschau.Video v) {
        started = true;
        title.setText(v.title.length() > 0 ? v.title : "tagesschau in 100 Sekunden");
        StringBuilder m = new StringBuilder();
        if (v.date.length() > 0) m.append(v.date);
        if (v.time.length() > 0) {
            if (m.length() > 0) m.append("  ·  ");
            m.append(v.time).append(" Uhr");
        }
        meta.setText(m.toString());
        android.util.Log.i("LEANTS", "spiele: " + v.url);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                spinner.setVisibility(View.GONE);
                layoutVideo(mp.getVideoWidth(), mp.getVideoHeight());
                video.start();
                ui.postDelayed(hideHint, 4000);
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                spinner.setVisibility(View.GONE);
                meta.setText("Wiedergabe fehlgeschlagen (Code " + what + ").");
                android.util.Log.w("LEANTS", "Player-Fehler " + what + "/" + extra);
                return true;
            }
        });
        video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) spinner.setVisibility(View.VISIBLE);
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) spinner.setVisibility(View.GONE);
                return false;
            }
        });

        video.setVideoURI(Uri.parse(v.url));
        spinner.setVisibility(View.VISIBLE);
        video.requestFocus();
    }

    /** Video 16:9 formatfuellend einsetzen (wie in LiveActivity). */
    private void layoutVideo(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int sw = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int sh = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;

        float videoRatio = (float) w / h;
        float screenRatio = (float) sw / sh;

        int vw, vh;
        if (videoRatio > screenRatio) {
            vw = sw;
            vh = (int) (sw / videoRatio);
        } else {
            vh = sh;
            vw = (int) (sh * videoRatio);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(vw, vh);
        lp.gravity = Gravity.CENTER;
        box.setLayoutParams(lp);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (hint != null) hint.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideHint);
        ui.postDelayed(hideHint, 4000);

        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (video != null && video.isPlaying()) {
                video.pause();
            } else if (video != null) {
                video.start();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (video != null) video.seekTo(Math.min(video.getDuration(),
                    video.getCurrentPosition() + 15000));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (video != null) video.seekTo(Math.max(0, video.getCurrentPosition() - 15000));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null && video.isPlaying()) video.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (video != null) {
            try { video.stopPlayback(); } catch (RuntimeException ignored) { }
        }
    }
}
