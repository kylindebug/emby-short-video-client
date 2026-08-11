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
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private ThumbnailLoader thumbnailLoader;
    private RandomPlaylist playlist;
    private PlaybackEngine engine;
    private PlaybackEngine previewEngine;
    private FrameLayout root;
    private FrameLayout videoDeck;
    private FrameLayout videoContainer;
    private FrameLayout previewContainer;
    private FrameLayout thumbnailPreview;
    private LinearLayout pausedControls;
    private LinearLayout progressOverlay;
    private TextView titleView;
    private TextView feedbackView;
    private TextView speedView;
    private TextView thumbnailTimeView;
    private ImageView thumbnailImageView;
    private TextView elapsedView;
    private TextView durationView;
    private SeekBar progressSeek;
    private ImageButton lockButton;
    private RotationController rotation;
    private long seekBaseMs;
    private boolean seekActive;
    private boolean manualSeekActive;
    private boolean longWasPlaying;
    private boolean longSpeedActive;
    private float speedBeforeLong = 1f;
    private boolean autoFallbackUsed;
    private boolean previewNext;
    private boolean switchAnimating;
    private Uri currentPlayableUri;
    private Uri previewPlayableUri;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        api = new EmbyApi(this, settings);
        preloadCache = new PreloadCache(this, api);
        thumbnailLoader = new ThumbnailLoader(this);
        rotation = new RotationController(this);
        buildUi();
        hideSystemUi();
        mainHandler.post(progressUpdater);
        loadLibrary();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        videoDeck = new FrameLayout(this);
        videoDeck.setClipChildren(true);
        videoContainer = new FrameLayout(this);
        videoDeck.addView(videoContainer, match());
        root.addView(videoDeck, match());

        GestureView gestures = new GestureView(this, this);
        root.addView(gestures, match());

        buildPausedControls();
        root.addView(pausedControls, match());

        buildThumbnailPreview();
        FrameLayout.LayoutParams thumbnailParams = new FrameLayout.LayoutParams(
                dp(184), dp(108), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        thumbnailParams.bottomMargin = dp(92);
        root.addView(thumbnailPreview, thumbnailParams);

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

        speedView = new TextView(this);
        UiStyle.stylePrimaryText(speedView, 15, true);
        speedView.setGravity(Gravity.CENTER);
        speedView.setAlpha(0.68f);
        speedView.setBackground(UiStyle.rounded(this, 0x661C2433, 14));
        speedView.setPadding(dp(14), dp(7), dp(14), dp(7));
        speedView.setVisibility(View.GONE);
        FrameLayout.LayoutParams speedParams = wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        speedParams.topMargin = dp(30);
        root.addView(speedView, speedParams);
        setContentView(root);
    }

    private void buildThumbnailPreview() {
        thumbnailPreview = new FrameLayout(this);
        thumbnailPreview.setPadding(dp(3), dp(3), dp(3), dp(3));
        thumbnailPreview.setBackground(UiStyle.rounded(this, 0xCC101722, 12, 0x99FFFFFF, 1));
        thumbnailPreview.setElevation(dp(10));
        thumbnailPreview.setVisibility(View.GONE);

        thumbnailImageView = new ImageView(this);
        thumbnailImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnailImageView.setBackgroundColor(Color.BLACK);
        thumbnailPreview.addView(thumbnailImageView, match());

        thumbnailTimeView = new TextView(this);
        UiStyle.stylePrimaryText(thumbnailTimeView, 12, true);
        thumbnailTimeView.setGravity(Gravity.CENTER);
        thumbnailTimeView.setBackground(UiStyle.rounded(this, 0x99000000, 9));
        thumbnailTimeView.setPadding(dp(9), dp(3), dp(9), dp(3));
        FrameLayout.LayoutParams timeParams = wrap(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        timeParams.bottomMargin = dp(7);
        thumbnailPreview.addView(thumbnailTimeView, timeParams);
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
                showThumbnail(target);
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
                hideThumbnailAfter(400);
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
        clearSwipePreview();
        if (engine != null) engine.release();
        videoContainer.removeAllViews();
        engine = newPlaybackEngine(decoder);
        engine.setListener(this);
        videoContainer.addView(engine.view(), match());
    }

    private PlaybackEngine newPlaybackEngine(AppSettings.Decoder decoder) {
        return decoder == AppSettings.Decoder.HARDWARE ? new Media3Engine(this) : new VlcEngine(this);
    }

    private void playCurrent(boolean autoPlay) {
        VideoItem item = playlist.current();
        titleView.setText(item.title);
        updateProgressInfo(0, -1);
        currentPlayableUri = preloadCache.playableUri(item);
        thumbnailLoader.cancel();
        engine.setSpeed(1f);
        engine.load(currentPlayableUri, autoPlay);
        setPausedUiVisible(!autoPlay);
        preloadCache.preload(playlist.upcoming(2));
    }

    private void switchVideo(boolean next) {
        if (playlist == null || playlist.isEmpty() || engine == null) return;
        clearSwipePreview();
        videoContainer.animate().cancel();
        videoContainer.setTranslationY(0f);
        if (next) playlist.next(); else playlist.previous();
        autoFallbackUsed = false;
        seekActive = false;
        manualSeekActive = false;
        longSpeedActive = false;
        speedView.setVisibility(View.GONE);
        hideThumbnail();
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

    @Override public void onSingleTap() {
        if (engine == null || !engine.isPlaying() || seekActive || manualSeekActive) return;
        if (progressOverlay.getVisibility() == View.VISIBLE) {
            hideProgressOverlayAnimated();
        } else {
            showProgressOverlay();
            hideProgressIfPlaying(3_000);
        }
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
        showThumbnail(target);
        showFeedback(formatTime(target) + "  (" + signedSeconds(actualDelta) + ")", 0);
        if (finished) {
            engine.seekTo(target);
            seekActive = false;
            showFeedback("已定位到 " + formatTime(target), 650);
            hideThumbnailAfter(400);
            hideProgressIfPlaying(650);
        }
    }

    @Override public void onVerticalDrag(float deltaPx, float heightPx) {
        if (engine == null || playlist == null || playlist.isEmpty() || switchAnimating || heightPx <= 0) return;
        boolean next = deltaPx < 0;
        prepareSwipePreview(next, heightPx);
        if (previewContainer == null) return;
        float offset = Math.max(-heightPx, Math.min(heightPx, deltaPx));
        videoContainer.setTranslationY(offset);
        previewContainer.setTranslationY(offset + (next ? heightPx : -heightPx));
        pausedControls.setVisibility(View.GONE);
        hideProgressOverlayNow();
        hideThumbnail();
    }

    @Override public void onVerticalRelease(float deltaPx, float heightPx, boolean commit) {
        if (switchAnimating) return;
        if (previewContainer == null || heightPx <= 0) {
            videoContainer.setTranslationY(0f);
            setPausedUiVisible(engine != null && !engine.isPlaying());
            return;
        }
        if (!commit) {
            animateSwipeBack(heightPx);
            return;
        }
        animateSwipeCommit(heightPx);
    }

    @Override public void onLongPressStart(boolean upperHalf) {
        if (engine == null) return;
        longWasPlaying = engine.isPlaying();
        longSpeedActive = true;
        speedBeforeLong = engine.speed();
        float rate = upperHalf ? settings.upperSpeed() : settings.lowerSpeed();
        engine.setSpeed(rate);
        if (!longWasPlaying) engine.play();
        speedView.setText(String.format(Locale.US, "%.3g×", rate));
        speedView.setVisibility(View.VISIBLE);
    }

    @Override public void onLongPressEnd() {
        if (engine == null || !longSpeedActive) return;
        engine.setSpeed(speedBeforeLong);
        if (!longWasPlaying) engine.pause();
        longSpeedActive = false;
        speedView.setVisibility(View.GONE);
    }

    private void prepareSwipePreview(boolean next, float heightPx) {
        if (previewContainer != null && previewNext == next) return;
        clearSwipePreview();
        previewNext = next;
        VideoItem item = playlist.adjacent(next);
        previewPlayableUri = preloadCache.playableUri(item);
        previewContainer = new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        previewContainer.setTranslationY(next ? heightPx : -heightPx);
        videoDeck.addView(previewContainer, match());
        previewEngine = newPlaybackEngine(settings.decoder());
        previewContainer.addView(previewEngine.view(), match());
        previewEngine.setSpeed(1f);
        previewEngine.load(previewPlayableUri, false);
    }

    private void animateSwipeBack(float heightPx) {
        switchAnimating = true;
        long durationMs = 180;
        DecelerateInterpolator easing = new DecelerateInterpolator();
        videoContainer.animate().cancel();
        previewContainer.animate().cancel();
        videoContainer.animate().translationY(0f).setDuration(durationMs).setInterpolator(easing).start();
        previewContainer.animate()
                .translationY(previewNext ? heightPx : -heightPx)
                .setDuration(durationMs)
                .setInterpolator(easing)
                .withEndAction(() -> {
                    clearSwipePreview();
                    setPausedUiVisible(engine != null && !engine.isPlaying());
                })
                .start();
    }

    private void animateSwipeCommit(float heightPx) {
        switchAnimating = true;
        long durationMs = 190;
        DecelerateInterpolator easing = new DecelerateInterpolator();
        videoContainer.animate().cancel();
        previewContainer.animate().cancel();
        videoContainer.animate()
                .translationY(previewNext ? -heightPx : heightPx)
                .setDuration(durationMs)
                .setInterpolator(easing)
                .start();
        previewContainer.animate()
                .translationY(0f)
                .setDuration(durationMs)
                .setInterpolator(easing)
                .withEndAction(this::finishSwipeCommit)
                .start();
    }

    private void finishSwipeCommit() {
        FrameLayout oldContainer = videoContainer;
        PlaybackEngine oldEngine = engine;
        boolean next = previewNext;
        videoContainer = previewContainer;
        engine = previewEngine;
        currentPlayableUri = previewPlayableUri;
        previewContainer = null;
        previewEngine = null;
        previewPlayableUri = null;
        switchAnimating = false;

        if (oldEngine != null) {
            oldEngine.setListener(null);
            oldEngine.release();
        }
        videoDeck.removeView(oldContainer);
        videoContainer.setTranslationY(0f);
        if (next) playlist.next(); else playlist.previous();
        autoFallbackUsed = false;
        seekActive = false;
        manualSeekActive = false;
        longSpeedActive = false;
        speedView.setVisibility(View.GONE);
        thumbnailLoader.cancel();
        titleView.setText(playlist.current().title);
        updateProgressInfo(0, -1);
        engine.setListener(this);
        engine.setSpeed(1f);
        engine.play();
        setPausedUiVisible(false);
        preloadCache.preload(playlist.upcoming(2));
    }

    private void clearSwipePreview() {
        if (previewContainer != null) previewContainer.animate().cancel();
        if (previewEngine != null) {
            previewEngine.setListener(null);
            previewEngine.release();
        }
        if (previewContainer != null) videoDeck.removeView(previewContainer);
        previewContainer = null;
        previewEngine = null;
        previewPlayableUri = null;
        switchAnimating = false;
    }

    private void setPausedUiVisible(boolean visible) {
        pausedControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible || seekActive || manualSeekActive) showProgressOverlay();
        else hideProgressOverlayNow();
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
        progressOverlay.animate().cancel();
        progressOverlay.setAlpha(1f);
        progressOverlay.setVisibility(View.VISIBLE);
    }

    private void hideProgressOverlayNow() {
        mainHandler.removeCallbacksAndMessages("progress-hide");
        progressOverlay.animate().cancel();
        progressOverlay.setAlpha(1f);
        progressOverlay.setVisibility(View.GONE);
    }

    private void hideProgressOverlayAnimated() {
        mainHandler.removeCallbacksAndMessages("progress-hide");
        if (progressOverlay.getVisibility() != View.VISIBLE) return;
        progressOverlay.animate().cancel();
        progressOverlay.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            progressOverlay.setVisibility(View.GONE);
            progressOverlay.setAlpha(1f);
        }).start();
    }

    private void hideProgressIfPlaying(long delayMs) {
        mainHandler.removeCallbacksAndMessages("progress-hide");
        if (engine == null || !engine.isPlaying()) return;
        mainHandler.postAtTime(() -> {
            if (engine != null && engine.isPlaying() && !seekActive && !manualSeekActive) {
                hideProgressOverlayAnimated();
            }
        }, "progress-hide", android.os.SystemClock.uptimeMillis() + delayMs);
    }

    private void showThumbnail(long positionMs) {
        if (currentPlayableUri == null) return;
        mainHandler.removeCallbacksAndMessages("thumbnail-hide");
        thumbnailTimeView.setText(formatTime(positionMs));
        thumbnailPreview.setVisibility(View.VISIBLE);
        thumbnailPreview.setAlpha(1f);
        thumbnailLoader.request(currentPlayableUri, positionMs, dp(320), dp(180), bitmap -> {
            if (thumbnailPreview.getVisibility() == View.VISIBLE) thumbnailImageView.setImageBitmap(bitmap);
        });
    }

    private void hideThumbnailAfter(long delayMs) {
        mainHandler.removeCallbacksAndMessages("thumbnail-hide");
        mainHandler.postAtTime(this::hideThumbnail, "thumbnail-hide",
                android.os.SystemClock.uptimeMillis() + delayMs);
    }

    private void hideThumbnail() {
        mainHandler.removeCallbacksAndMessages("thumbnail-hide");
        thumbnailLoader.cancel();
        thumbnailPreview.setVisibility(View.GONE);
        thumbnailImageView.setImageDrawable(null);
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
        if (engine != null && longSpeedActive) {
            engine.setSpeed(speedBeforeLong);
            longSpeedActive = false;
        }
        speedView.setVisibility(View.GONE);
        hideThumbnail();
        if (engine != null) engine.pause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST && resultCode == RESULT_OK) recreate();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        clearSwipePreview();
        videoContainer.setTranslationY(0f);
        hideSystemUi();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        rotation.stop();
        clearSwipePreview();
        if (engine != null) engine.release();
        thumbnailLoader.release();
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
