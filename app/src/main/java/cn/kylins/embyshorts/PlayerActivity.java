package cn.kylins.embyshorts;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import android.widget.Button;
import android.widget.ProgressBar;

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
            if (!activityPaused) updatePlaybackStatus();
            mainHandler.postDelayed(this, 250);
        }
    };
    private AppSettings settings;
    private EmbyApi api;
    private PlaybackCache playbackCache;
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
    private LinearLayout loadingPanel;
    private LinearLayout recoveryActions;
    private TextView loadingText;
    private ProgressBar loadingSpinner;
    private Button softwareButton;
    private boolean failureHandled;
    private boolean activityPaused;
    private boolean destroyed;
    private final PlaybackHealth playbackHealth = new PlaybackHealth();
    private final SpeedTransition speedTransition = new SpeedTransition();
    private PlaybackEngine speedEngine;
    private final Runnable speedStep = new Runnable() {
        @Override public void run() {
            if (speedEngine == null || speedEngine != engine || destroyed) return;
            long now = SystemClock.uptimeMillis();
            speedEngine.setSpeed(speedTransition.value(now));
            if (!speedTransition.finished(now)) mainHandler.postDelayed(this, 25);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        api = new EmbyApi(this, settings);
        playbackCache = new PlaybackCache(this);
        thumbnailLoader = new ThumbnailLoader(this);
        rotation = new RotationController(this);
        buildUi();
        hideSystemUi();
        mainHandler.post(progressUpdater);
        io.execute(playbackCache::runMaintenance);
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
        buildLoadingPanel();
        setContentView(root);
    }

    private void buildLoadingPanel() {
        loadingPanel = new LinearLayout(this);
        loadingPanel.setOrientation(LinearLayout.VERTICAL);
        loadingPanel.setGravity(Gravity.CENTER);
        loadingPanel.setPadding(dp(16), dp(14), dp(16), dp(14));
        loadingPanel.setBackground(UiStyle.rounded(this, 0xE61C2433, 18));
        loadingSpinner = new ProgressBar(this);
        loadingPanel.addView(loadingSpinner, new LinearLayout.LayoutParams(dp(28), dp(28)));
        loadingText = new TextView(this);
        UiStyle.stylePrimaryText(loadingText, 14, false);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, dp(10), 0, dp(10));
        loadingPanel.addView(loadingText);
        recoveryActions = new LinearLayout(this);
        recoveryActions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.addView(recoveryButton("重试", () -> retryCurrent(false)), actionParams());
        firstRow.addView(recoveryButton("下一条", () -> switchVideo(true)), actionParams());
        recoveryActions.addView(firstRow);
        LinearLayout secondRow = new LinearLayout(this);
        softwareButton = recoveryButton("尝试软解", () -> retryCurrent(true));
        secondRow.addView(softwareButton, actionParams());
        secondRow.addView(recoveryButton("设置", () -> {
            if (engine != null) engine.pause();
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST);
        }), actionParams());
        recoveryActions.addView(secondRow);
        loadingPanel.addView(recoveryActions);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        params.leftMargin = params.rightMargin = dp(24);
        root.addView(loadingPanel, params);
        loadingPanel.setVisibility(View.GONE);
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private Button recoveryButton(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        UiStyle.styleSecondaryButton(button);
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void resetPlaybackStatus() {
        failureHandled = false;
        playbackHealth.reset(SystemClock.uptimeMillis());
        loadingPanel.setVisibility(View.GONE);
        mainHandler.removeCallbacks(speedStep);
        speedEngine = null;
    }

    private void updatePlaybackStatus() {
        if (engine == null || destroyed) return;
        PlaybackStatus state = engine.status();
        if (state.failure != null && !failureHandled) {
            onError(state.failure);
            return;
        }
        long waiting = playbackHealth.waitingMs(state, engine.playWhenReady(),
                engine.positionMs(), SystemClock.uptimeMillis());
        boolean failed = state.failure != null;
        boolean stalled = waiting >= PlaybackHealth.ACTION_DELAY_MS;
        if ((!failed && waiting < PlaybackHealth.SHOW_DELAY_MS) || previewContainer != null) {
            loadingPanel.setVisibility(View.GONE);
            return;
        }
        String message;
        if (failed) message = state.failure.message() + "\n" + state.failure.code;
        else {
            message = state.buffering ? "正在加载视频…" : !state.videoOutput
                    ? "正在等待视频画面…" : "播放暂时没有前进…";
            if (state.bufferingPercent >= 0) message += "\n缓冲 " + state.bufferingPercent + "%";
            else if (state.bufferedMs > 0) message += String.format(Locale.CHINA,
                    "\n已缓冲 %.1f 秒", state.bufferedMs / 1000f);
            message += "\n已等待 " + waiting / 1000 + " 秒";
            if (stalled) message += "\n等待较久，可能是网络或解码问题";
        }
        loadingText.setText(message + "\n仍可上下滑换片或返回");
        loadingSpinner.setVisibility(failed || stalled ? View.GONE : View.VISIBLE);
        recoveryActions.setVisibility(failed || stalled ? View.VISIBLE : View.GONE);
        softwareButton.setVisibility(engine instanceof Media3Engine ? View.VISIBLE : View.GONE);
        loadingPanel.setVisibility(View.VISIBLE);
    }

    private void retryCurrent(boolean software) {
        if (engine == null || playlist == null || playlist.isEmpty()) return;
        long position = Math.max(0, engine.positionMs());
        AppSettings.Decoder decoder = software || engine instanceof VlcEngine
                ? AppSettings.Decoder.SOFTWARE : settings.decoder();
        onLongPressEnd();
        boolean wantsPlay = engine.playWhenReady();
        createEngine(decoder);
        autoFallbackUsed = decoder == AppSettings.Decoder.SOFTWARE;
        resetPlaybackStatus();
        engine.load(currentPlayableUri, wantsPlay);
        if (position > 0) engine.seekTo(position);
        setPausedUiVisible(!wantsPlay);
    }

    private void bindEngineListener(PlaybackEngine source) {
        source.setListener(new PlaybackEngine.Listener() {
            private void dispatch(Runnable action) {
                mainHandler.post(() -> {
                    if (!destroyed && !activityPaused && engine == source) action.run();
                });
            }
            @Override public void onEnded() { dispatch(PlayerActivity.this::onEnded); }
            @Override public void onError(PlaybackFailure failure) {
                dispatch(() -> PlayerActivity.this.onError(failure));
            }
            @Override public void onPlayingChanged(boolean playing) {
                dispatch(() -> PlayerActivity.this.onPlayingChanged(playing));
            }
        });
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
        pausedControls.setBackgroundColor(Color.TRANSPARENT);

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
                    if (destroyed || isFinishing()) return;
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
                    if (destroyed || isFinishing()) return;
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
        mainHandler.removeCallbacks(speedStep);
        speedEngine = null;
        if (engine != null) {
            engine.setListener(null);
            engine.release();
        }
        videoContainer.animate().cancel();
        videoContainer.setTranslationY(0f);
        videoContainer.removeAllViews();
        engine = newPlaybackEngine(decoder);
        bindEngineListener(engine);
        videoContainer.addView(engine.view(), match());
    }

    private PlaybackEngine newPlaybackEngine(AppSettings.Decoder decoder) {
        return decoder == AppSettings.Decoder.HARDWARE
                ? new Media3Engine(this, playbackCache) : new VlcEngine(this);
    }

    private void playCurrent(boolean autoPlay) {
        resetPlaybackStatus();
        VideoItem item = playlist.current();
        titleView.setText(item.title);
        updateProgressInfo(0, -1);
        currentPlayableUri = Uri.parse(api.streamUrl(item));
        thumbnailLoader.cancel();
        engine.setSpeed(1f);
        engine.load(currentPlayableUri, autoPlay && !activityPaused);
        setPausedUiVisible(!autoPlay);
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
        hideGestureStatus();
        hideThumbnail();
        createEngine(settings.decoder());
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

    @Override public void onError(PlaybackFailure failure) {
        runOnUiThread(() -> {
            if (engine == null || destroyed || failureHandled) return;
            failureHandled = true;
            if (failure.kind == PlaybackFailure.Kind.DECODE && engine instanceof Media3Engine && !autoFallbackUsed) {
                autoFallbackUsed = true;
                long position = Math.max(0, engine.positionMs());
                onLongPressEnd();
                boolean wantsPlay = engine.playWhenReady();
                createEngine(AppSettings.Decoder.SOFTWARE);
                resetPlaybackStatus();
                engine.load(currentPlayableUri, wantsPlay);
                if (position > 0) engine.seekTo(position);
                Toast.makeText(this, "硬解失败，已自动切换软解", Toast.LENGTH_LONG).show();
            } else {
                setPausedUiVisible(true);
                updatePlaybackStatus();
            }
        });
    }

    @Override public void onPlayingChanged(boolean playing) {
        if (engine != null && previewContainer == null) {
            setPausedUiVisible(!engine.playWhenReady() || engine.status().failure != null);
        }
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
        if (engine.status().failure != null) { retryCurrent(false); return; }
        if (engine.playWhenReady()) engine.pause(); else engine.play();
        setPausedUiVisible(!engine.playWhenReady());
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
        showGestureStatus(formatTime(target) + "  (" + signedSeconds(actualDelta) + ")", 0);
        if (finished) {
            engine.seekTo(target);
            seekActive = false;
            showGestureStatus("已定位到 " + formatTime(target), 650);
            hideThumbnailAfter(400);
            hideProgressIfPlaying(650);
        }
    }

    @Override public void onVerticalDrag(float deltaPx, float heightPx) {
        if (engine == null || playlist == null || playlist.isEmpty() || heightPx <= 0) return;
        if (switchAnimating) {
            videoContainer.animate().cancel();
            clearSwipePreview();
            videoContainer.setTranslationY(0f);
        }
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
            setPausedUiVisible(engine != null && !engine.playWhenReady());
            return;
        }
        if (!commit) {
            animateSwipeBack(heightPx);
            return;
        }
        animateSwipeCommit(heightPx);
    }

    @Override public void onLongPressStart(boolean upperHalf) {
        if (engine == null || longSpeedActive || switchAnimating || engine.status().failure != null) return;
        longWasPlaying = engine.playWhenReady();
        longSpeedActive = true;
        // A second hold can interrupt the previous restoration ramp.
        if (speedEngine != engine || speedTransition.finished(SystemClock.uptimeMillis())) {
            speedBeforeLong = engine.speed();
        }
        float rate = upperHalf ? settings.upperSpeed() : settings.lowerSpeed();
        transitionSpeed(rate);
        if (!longWasPlaying) engine.play();
        showGestureStatus(String.format(Locale.US, "%.3g×", rate), 0);
    }

    @Override public void onLongPressEnd() {
        if (engine == null || !longSpeedActive) return;
        longSpeedActive = false;
        hideGestureStatus();
        if (!longWasPlaying) {
            mainHandler.removeCallbacks(speedStep);
            engine.pause();
            engine.setSpeed(speedBeforeLong);
        } else transitionSpeed(speedBeforeLong);
    }

    private void transitionSpeed(float target) {
        mainHandler.removeCallbacks(speedStep);
        speedEngine = engine;
        long now = SystemClock.uptimeMillis();
        // Apply the first small step immediately; no pause/seek/prepare during a playing hold.
        speedTransition.start(engine.speed(), target, now - 25);
        speedStep.run();
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackGesture() {
        onBackPressed();
    }

    private void prepareSwipePreview(boolean next, float heightPx) {
        if (previewContainer != null && previewNext == next) return;
        clearSwipePreview();
        previewNext = next;
        VideoItem item = playlist.adjacent(next);
        previewPlayableUri = Uri.parse(api.streamUrl(item));
        previewContainer = new FrameLayout(this);
        previewContainer.setBackgroundColor(Color.BLACK);
        previewContainer.setTranslationY(next ? heightPx : -heightPx);
        videoDeck.addView(previewContainer, match());
        TextView previewLabel = new TextView(this);
        UiStyle.stylePrimaryText(previewLabel, 16, true);
        previewLabel.setGravity(Gravity.CENTER);
        previewLabel.setText(item.title + "\n松手播放");
        previewContainer.addView(previewLabel, match());
        // Do not open a second native software decoder for every swipe movement.
        if (settings.decoder() == AppSettings.Decoder.HARDWARE) {
            previewEngine = newPlaybackEngine(settings.decoder());
            previewContainer.addView(previewEngine.view(), match());
            previewEngine.load(previewPlayableUri, false);
        }
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
                    setPausedUiVisible(engine != null && !engine.playWhenReady());
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
        if (previewContainer == null || destroyed || activityPaused) {
            clearSwipePreview();
            videoContainer.setTranslationY(0f);
            return;
        }
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
        hideGestureStatus();
        thumbnailLoader.cancel();
        titleView.setText(playlist.current().title);
        updateProgressInfo(0, -1);
        resetPlaybackStatus();
        if (engine == null) {
            videoContainer.removeAllViews();
            engine = newPlaybackEngine(settings.decoder());
            videoContainer.addView(engine.view(), match());
            engine.load(currentPlayableUri, false);
        }
        bindEngineListener(engine);
        engine.setSpeed(1f);
        engine.play();
        setPausedUiVisible(false);
        updatePlaybackStatus();
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

    private void showGestureStatus(String text, long hideAfterMs) {
        mainHandler.removeCallbacksAndMessages("gesture-status");
        speedView.animate().cancel();
        speedView.setText(text);
        speedView.setAlpha(0.68f);
        speedView.setVisibility(View.VISIBLE);
        if (hideAfterMs > 0) {
            mainHandler.postAtTime(this::hideGestureStatus, "gesture-status",
                    android.os.SystemClock.uptimeMillis() + hideAfterMs);
        }
    }

    private void hideGestureStatus() {
        mainHandler.removeCallbacksAndMessages("gesture-status");
        speedView.animate().cancel();
        speedView.setVisibility(View.GONE);
    }

    @Override protected void onResume() {
        super.onResume();
        activityPaused = false;
        playbackHealth.reset(SystemClock.uptimeMillis());
        if (engine != null) setPausedUiVisible(!engine.playWhenReady());
        rotation.start();
        hideSystemUi();
    }

    @Override protected void onPause() {
        super.onPause();
        activityPaused = true;
        rotation.stop();
        mainHandler.removeCallbacks(speedStep);
        if (engine != null && speedEngine == engine) engine.setSpeed(speedBeforeLong);
        speedEngine = null;
        videoContainer.animate().cancel();
        clearSwipePreview();
        videoContainer.setTranslationY(0f);
        if (engine != null && longSpeedActive) {
            engine.setSpeed(speedBeforeLong);
            longSpeedActive = false;
        }
        hideGestureStatus();
        hideThumbnail();
        if (engine != null) engine.pause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST && resultCode == RESULT_OK) recreate();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onLongPressEnd();
        videoContainer.animate().cancel();
        clearSwipePreview();
        videoContainer.setTranslationY(0f);
        hideSystemUi();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        rotation.stop();
        clearSwipePreview();
        if (engine != null) engine.release();
        thumbnailLoader.release();
        playbackCache.release();
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
