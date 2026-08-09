package cn.kylins.embyshorts;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlayerActivity extends Activity implements PlaybackEngine.Listener, GestureView.Callback {
    private static final int SETTINGS_REQUEST = 11;
    private static final int PROGRESS_MAX = 10_000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (engine != null && !seekActive && !manualSeekActive) syncProgressFromPlayer();
            mainHandler.postDelayed(this, 250);
        }
    };
    private AppSettings settings;
    private EmbyApi api;
    private PreloadCache preloadCache;
    private RandomPlaylist playlist;
    private PlaybackEngine engine;
    private FrameLayout root;
    private FrameLayout videoContainer;
    private LinearLayout pausedControls;
    private LinearLayout progressOverlay;
    private TextView titleView;
    private TextView feedbackView;
    private TextView elapsedView;
    private TextView durationView;
    private SeekBar progressSeek;
    private ImageButton lockButton;
    private RotationController rotation;
    private long seekBaseMs;
    private boolean seekActive;
    private boolean manualSeekActive;
    private boolean longWasPlaying;
    private float speedBeforeLong = 1f;
    private boolean autoFallbackUsed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        api = new EmbyApi(this, settings);
        preloadCache = new PreloadCache(this, api);
        rotation = new RotationController(this);
        buildUi();
        hideSystemUi();
        mainHandler.post(progressUpdater);
        loadLibrary();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        videoContainer = new FrameLayout(this);
        root.addView(videoContainer, match());

        GestureView gestures = new GestureView(this, this);
        root.addView(gestures, match());

        buildPausedControls();
        root.addView(pausedControls, match());

        buildProgressOverlay();
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        progressParams.leftMargin = dp(12);
        progressParams.rightMargin = dp(12);
        progressParams.bottomMargin = dp(10);
        root.addView(progressOverlay, progressParams);

        feedbackView = new TextView(this);
        UiStyle.stylePrimaryText(feedbackView, 16, true);
        feedbackView.setGravity(Gravity.CENTER);
        feedbackView.setBackground(UiStyle.rounded(this, 0xE61C2433, 18, UiStyle.OUTLINE, 1));
        feedbackView.setElevation(dp(8));
        feedbackView.setPadding(dp(18), dp(10), dp(18), dp(10));
        feedbackView.setVisibility(View.GONE);
        root.addView(feedbackView, wrap(Gravity.CENTER));
        setContentView(root);
    }

    private void buildPausedControls() {
        pausedControls = new LinearLayout(this);
        pausedControls.setOrientation(LinearLayout.VERTICAL);
        pausedControls.setPadding(dp(18), dp(14), dp(18), dp(18));
        pausedControls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD90A0E16, 0x220A0E16, 0xB80A0E16}));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        titleView = new TextView(this);
        UiStyle.stylePrimaryText(titleView, 19, true);
        titleView.setLetterSpacing(0.01f);
        titleView.setMaxLines(2);
        top.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton orientationButton = iconButton(R.drawable.ic_screen_rotation, "切换横竖屏");
        orientationButton.setOnClickListener(v -> rotation.toggleManualOrientation());
        top.addView(orientationButton);

        lockButton = iconButton(R.drawable.ic_lock_open, "锁定屏幕方向");
        lockButton.setOnClickListener(v -> {
            rotation.toggleLock();
            lockButton.setImageResource(rotation.isLocked() ? R.drawable.ic_lock : R.drawable.ic_lock_open);
            lockButton.setContentDescription(rotation.isLocked() ? "解除方向锁定" : "锁定屏幕方向");
        });
        top.addView(lockButton);

        ImageButton settingsButton = iconButton(R.drawable.ic_settings, "打开设置");
        settingsButton.setOnClickListener(v -> {
            if (engine != null) engine.pause();
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST);
        });
        top.addView(settingsButton);
        pausedControls.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(this);
        pausedControls.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView hint = new TextView(this);
        hint.setText("双击播放/暂停  ·  上下滑换片  ·  左右滑进度  ·  长按变速");
        UiStyle.styleSecondaryText(hint, 13);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.bottomMargin = dp(70);
        pausedControls.addView(hint, hintParams);
    }

    private void buildProgressOverlay() {
        progressOverlay = new LinearLayout(this);
        progressOverlay.setOrientation(LinearLayout.VERTICAL);
        progressOverlay.setPadding(dp(16), dp(9), dp(16), dp(11));
        progressOverlay.setBackground(UiStyle.rounded(this, 0xF01A2130, 16, UiStyle.OUTLINE, 1));
        progressOverlay.setElevation(dp(8));

        progressSeek = new SeekBar(this);
        progressSeek.setMax(PROGRESS_MAX);
        progressSeek.setProgress(0);
        UiStyle.styleSeekBar(progressSeek);
        progressSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || engine == null) return;
                long duration = engine.durationMs();
                long target = duration > 0 ? Math.round(duration * (progress / (double) PROGRESS_MAX)) : 0;
                updateProgressInfo(target, duration);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                manualSeekActive = true;
                showProgressOverlay();
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (engine != null && engine.durationMs() > 0) {
                    long target = Math.round(engine.durationMs() * (bar.getProgress() / (double) PROGRESS_MAX));
                    engine.seekTo(target);
                    updateProgressInfo(target, engine.durationMs());
                }
                manualSeekActive = false;
                hideProgressIfPlaying(650);
            }
        });
        progressOverlay.addView(progressSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout times = new LinearLayout(this);
        elapsedView = timeLabel(Gravity.START);
        durationView = timeLabel(Gravity.END);
        times.addView(elapsedView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(durationView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        progressOverlay.addView(times, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateProgressInfo(0, -1);
        progressOverlay.setVisibility(View.GONE);
    }

    private void loadLibrary() {
        showFeedback("正在读取媒体库…", 0);
        io.execute(() -> {
            try {
                List<VideoItem> videos = api.getVideos();
                runOnUiThread(() -> {
                    hideFeedback();
                    playlist = new RandomPlaylist(videos);
                    if (playlist.isEmpty()) {
                        titleView.setText("所选位置没有视频");
                        setPausedUiVisible(true);
                    } else {
                        createEngine(settings.decoder());
                        playCurrent(settings.startMode() == AppSettings.StartMode.AUTO_PLAY);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    hideFeedback();
                    titleView.setText(String.format(Locale.CHINA, "无法读取媒体库：%s", readable(error)));
                    setPausedUiVisible(true);
                    Toast.makeText(this, readable(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void createEngine(AppSettings.Decoder decoder) {
        if (engine != null) engine.release();
        videoContainer.removeAllViews();
        engine = decoder == AppSettings.Decoder.HARDWARE ? new Media3Engine(this) : new VlcEngine(this);
        engine.setListener(this);
        videoContainer.addView(engine.view(), match());
    }

    private void playCurrent(boolean autoPlay) {
        VideoItem item = playlist.current();
        titleView.setText(item.title);
        updateProgressInfo(0, -1);
        Uri uri = preloadCache.playableUri(item);
        engine.setSpeed(1f);
        engine.load(uri, autoPlay);
        setPausedUiVisible(!autoPlay);
        preloadCache.preload(playlist.upcoming(2));
    }

    private void switchVideo(boolean next) {
        if (playlist == null || playlist.isEmpty() || engine == null) return;
        if (next) playlist.next(); else playlist.previous();
        autoFallbackUsed = false;
        seekActive = false;
        manualSeekActive = false;
        playCurrent(true);
    }

    @Override public void onEnded() {
        runOnUiThread(() -> {
            if (engine == null) return;
            switch (settings.endMode()) {
                case NEXT -> switchVideo(true);
                case PAUSE -> {
                    engine.pause();
                    engine.seekTo(Math.max(0, engine.durationMs()));
                    syncProgressFromPlayer();
                    setPausedUiVisible(true);
                }
                case LOOP -> {
                    engine.seekTo(0);
                    engine.play();
                }
            }
        });
    }

    @Override public void onError(String message) {
        runOnUiThread(() -> {
            if (settings.decoder() == AppSettings.Decoder.HARDWARE && !autoFallbackUsed && engine != null) {
                autoFallbackUsed = true;
                long position = engine.positionMs();
                createEngine(AppSettings.Decoder.SOFTWARE);
                engine.load(preloadCache.playableUri(playlist.current()), true);
                mainHandler.postDelayed(() -> engine.seekTo(position), 400);
                Toast.makeText(this, "硬解失败，已自动切换软解", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                setPausedUiVisible(true);
            }
        });
    }

    @Override public void onPlayingChanged(boolean playing) {
        runOnUiThread(() -> setPausedUiVisible(!playing));
    }

    @Override public void onDoubleTap() {
        if (engine == null) return;
        if (engine.isPlaying()) engine.pause(); else engine.play();
    }

    @Override public void onSeekGesture(float deltaPx, float widthPx, boolean finished) {
        if (engine == null) return;
        if (!seekActive) {
            seekActive = true;
            seekBaseMs = engine.positionMs();
        }
        long duration = engine.durationMs();
        long requestedDelta = SeekMath.deltaMs(deltaPx, widthPx, duration);
        long actualDelta = SeekMath.clampedDeltaMs(seekBaseMs, requestedDelta, duration);
        long target = seekBaseMs + actualDelta;
        updateProgressInfo(target, duration);
        showProgressOverlay();
        showFeedback(formatTime(target) + "  (" + signedSeconds(actualDelta) + ")", 0);
        if (finished) {
            engine.seekTo(target);
            seekActive = false;
            showFeedback("已定位到 " + formatTime(target), 650);
            hideProgressIfPlaying(650);
        }
    }

    @Override public void onVerticalSwitch(boolean next) { switchVideo(next); }

    @Override public void onLongPressStart(boolean upperHalf) {
        if (engine == null) return;
        longWasPlaying = engine.isPlaying();
        speedBeforeLong = engine.speed();
        float rate = upperHalf ? settings.upperSpeed() : settings.lowerSpeed();
        engine.setSpeed(rate);
        if (!longWasPlaying) engine.play();
        showFeedback(String.format(Locale.US, "%.3g×", rate), 0);
    }

    @Override public void onLongPressEnd() {
        if (engine == null) return;
        engine.setSpeed(speedBeforeLong);
        if (!longWasPlaying) engine.pause();
        hideFeedback();
    }

    private void setPausedUiVisible(boolean visible) {
        pausedControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible || seekActive || manualSeekActive) showProgressOverlay();
        else progressOverlay.setVisibility(View.GONE);
        if (visible) syncProgressFromPlayer();
    }

    private void syncProgressFromPlayer() {
        if (engine == null) return;
        updateProgressInfo(engine.positionMs(), engine.durationMs());
    }

    private void updateProgressInfo(long positionMs, long durationMs) {
        long safePosition = Math.max(0, positionMs);
        elapsedView.setText(formatTime(safePosition));
        durationView.setText(durationMs > 0 ? formatTime(durationMs) : "--:--");
        if (!manualSeekActive) {
            int progress = durationMs > 0
                    ? (int) Math.max(0, Math.min(PROGRESS_MAX, Math.round(safePosition * PROGRESS_MAX / (double) durationMs)))
                    : 0;
            progressSeek.setProgress(progress);
        }
        progressSeek.setEnabled(durationMs > 0);
    }

    private void showProgressOverlay() {
        mainHandler.removeCallbacksAndMessages("progress-hide");
        progressOverlay.setVisibility(View.VISIBLE);
    }

    private void hideProgressIfPlaying(long delayMs) {
        mainHandler.removeCallbacksAndMessages("progress-hide");
        if (engine == null || !engine.isPlaying()) return;
        mainHandler.postAtTime(() -> {
            if (engine != null && engine.isPlaying() && !seekActive && !manualSeekActive) {
                progressOverlay.setVisibility(View.GONE);
            }
        }, "progress-hide", android.os.SystemClock.uptimeMillis() + delayMs);
    }

    private void showFeedback(String text, long hideAfterMs) {
        feedbackView.setText(text);
        feedbackView.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacksAndMessages("feedback");
        if (hideAfterMs > 0) {
            mainHandler.postAtTime(this::hideFeedback, "feedback", android.os.SystemClock.uptimeMillis() + hideAfterMs);
        }
    }

    private void hideFeedback() { feedbackView.setVisibility(View.GONE); }

    @Override protected void onResume() {
        super.onResume();
        rotation.start();
        hideSystemUi();
    }

    @Override protected void onPause() {
        super.onPause();
        rotation.stop();
        if (engine != null) engine.pause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST && resultCode == RESULT_OK) recreate();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUi();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        rotation.stop();
        if (engine != null) engine.release();
        preloadCache.release();
        io.shutdownNow();
        super.onDestroy();
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        UiStyle.styleIconButton(button, true);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private TextView timeLabel(int gravity) {
        TextView view = new TextView(this);
        UiStyle.styleSecondaryText(view, 13);
        view.setTypeface(UiStyle.MEDIUM);
        view.setGravity(gravity);
        return view;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String readable(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static String signedSeconds(long ms) {
        if (ms == 0) return "0.0 秒";
        return String.format(Locale.US, "%+.1f 秒", ms / 1000f);
    }

    private static String formatTime(long ms) {
        long seconds = Math.max(0, ms / 1000);
        if (seconds >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        }
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
