package io.github.leanlauncher;

import android.app.Activity;
import android.content.pm.ActivityInfo;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

/** Wiedergabe eines Livestreams: 16:9 formatfuellend, Zappen mit links/rechts. */
public class LiveActivity extends Activity {

    private VideoView video;
    private FrameLayout box, root;
    private TextView overlayName, hint;
    private ProgressBar spinner;
    private int idx = 0;
    private int vidW = 0, vidH = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        String name = getIntent().getStringExtra("name");
        String url = getIntent().getStringExtra("url");
        for (int i = 0; i < Channels.NAMES.length; i++) {
            if (Channels.NAMES[i].equals(name)) { idx = i; break; }
        }

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

        overlayName = new TextView(this);
        overlayName.setTextColor(Color.WHITE);
        overlayName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        overlayName.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable ob = new GradientDrawable();
        ob.setColor(0xCC000000); ob.setCornerRadius(dp(12));
        overlayName.setBackground(ob);
        overlayName.setPadding(dp(20), dp(10), dp(20), dp(10));
        FrameLayout.LayoutParams onp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        onp.gravity = Gravity.TOP | Gravity.LEFT;
        onp.leftMargin = dp(28); onp.topMargin = dp(22);
        overlayName.setVisibility(View.GONE);
        root.addView(overlayName, onp);

        hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        hint.setGravity(Gravity.CENTER);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xB3000000); hb.setCornerRadius(dp(12));
        hint.setBackground(hb);
        hint.setPadding(dp(20), dp(10), dp(20), dp(10));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(34);
        hint.setVisibility(View.GONE);
        root.addView(hint, hp);

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.CENTER;
        root.addView(spinner, sp);

        setContentView(root);

        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int bo,
                                       int ol, int ot, int or, int ob) {
                sizeBox(); fitVideo();
            }
        });

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                spinner.setVisibility(View.GONE);
                vidW = mp.getVideoWidth();
                vidH = mp.getVideoHeight();
                mp.setLooping(false);
                sizeBox();
                video.start();
                video.post(new Runnable() { public void run() { fitVideo(); } });
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                spinner.setVisibility(View.GONE);
                showHint("Sender nicht erreichbar\nOK = erneut versuchen");
                return true;
            }
        });
        video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) spinner.setVisibility(View.VISIBLE);
                else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) spinner.setVisibility(View.GONE);
                return false;
            }
        });

        video.requestFocus();
        play(url);
        showName();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void play(String url) {
        if (url == null) { playChannel(idx); return; }
        spinner.setVisibility(View.VISIBLE);
        video.setVideoURI(Uri.parse(url));
    }

    private void playChannel(int i) {
        idx = ((i % Channels.NAMES.length) + Channels.NAMES.length) % Channels.NAMES.length;
        vidW = vidH = 0;
        spinner.setVisibility(View.VISIBLE);
        video.setVideoURI(Uri.parse(Channels.URLS[idx]));
        showName();
    }

    private void showName() {
        overlayName.setText(Channels.NAMES[idx]);
        overlayName.setVisibility(View.VISIBLE);
        ui.postDelayed(new Runnable() {
            public void run() { overlayName.setVisibility(View.GONE); }
        }, 2600);
    }

    private void showHint(String s) {
        hint.setText(s);
        hint.setVisibility(View.VISIBLE);
        ui.postDelayed(new Runnable() {
            public void run() { hint.setVisibility(View.GONE); }
        }, 2400);
    }

    private void sizeBox() {
        int dw = root.getWidth(), dh = root.getHeight();
        if (dw <= 0 || dh <= 0) return;
        int w, h;
        if (dw * 9 <= dh * 16) { w = dw; h = dw * 9 / 16; }
        else { h = dh; w = dh * 16 / 9; }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; box.setLayoutParams(lp); }
    }

    /** Fuellt die 16:9-Box komplett - keine Balken. */
    private void fitVideo() {
        if (video == null || box == null) return;
        int bw = box.getWidth(), bh = box.getHeight();
        if (bw <= 0 || bh <= 0 || vidW <= 0 || vidH <= 0) return;
        float vAR = (float) vidW / vidH, bAR = (float) bw / bh;
        float scale = (vAR > bAR) ? (vAR / bAR) : (bAR / vAR);
        if (scale < 1f) scale = 1f;
        if (scale > 1.5f) scale = 1.5f;
        video.setScaleX(scale);
        video.setScaleY(scale);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        ui.postDelayed(new Runnable() { public void run() { sizeBox(); fitVideo(); } }, 300);
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

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                playChannel(idx + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                playChannel(idx - 1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (!video.isPlaying()) playChannel(idx);
                return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (video != null) video.stopPlayback();
    }
}
