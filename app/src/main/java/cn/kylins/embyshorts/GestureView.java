package cn.kylins.embyshorts;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

@SuppressLint("ViewConstructor")
public final class GestureView extends View {
    public interface Callback {
        void onSingleTap();
        void onDoubleTap();
        void onSeekGesture(float deltaPx, float widthPx, boolean finished);
        void onVerticalDrag(float deltaPx, float heightPx);
        void onVerticalRelease(float deltaPx, float heightPx, boolean commit);
        void onLongPressStart(boolean upperHalf);
        void onLongPressEnd();
    }

    private enum Mode { NONE, HORIZONTAL, VERTICAL }
    private final GestureDetector detector;
    private final float threshold;
    private final float density;
    private final float longPressSlop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private Mode mode = Mode.NONE;
    private float downX;
    private float downY;
    private boolean longPressActive;
    private boolean touchSequenceActive;
    private final Runnable activateLongPress;

    public GestureView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        activateLongPress = () -> {
            if (!touchSequenceActive || mode != Mode.NONE || longPressActive) return;
            longPressActive = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            this.callback.onLongPressStart(downY < getHeight() / 2f);
        };
        density = getResources().getDisplayMetrics().density;
        threshold = 24f * density;
        longPressSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                GestureView.this.callback.onSingleTap();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                cancelPendingLongPress();
                GestureView.this.callback.onDoubleTap();
                return true;
            }
        });
        detector.setIsLongpressEnabled(false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            mode = Mode.NONE;
            longPressActive = false;
            touchSequenceActive = true;
            handler.removeCallbacks(activateLongPress);
            handler.postDelayed(activateLongPress, GesturePolicy.LONG_PRESS_ACTIVATION_MS);
        }
        detector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !longPressActive) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (GesturePolicy.shouldCancelLongPress(dx, dy, longPressSlop)) cancelPendingLongPress();
            if (mode == Mode.NONE && Math.hypot(dx, dy) >= threshold) {
                mode = Math.abs(dx) >= Math.abs(dy) ? Mode.HORIZONTAL : Mode.VERTICAL;
            }
            if (mode == Mode.HORIZONTAL) callback.onSeekGesture(dx, getWidth(), false);
            else if (mode == Mode.VERTICAL && GesturePolicy.allowVerticalSwitch(downY, dy, getHeight(), density)) {
                callback.onVerticalDrag(dy, getHeight());
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancelPendingLongPress();
            if (longPressActive) {
                callback.onLongPressEnd();
            } else if (mode == Mode.HORIZONTAL) {
                callback.onSeekGesture(event.getX() - downX, getWidth(), true);
            } else if (mode == Mode.VERTICAL) {
                float dy = event.getY() - downY;
                boolean allowed = GesturePolicy.allowVerticalSwitch(downY, dy, getHeight(), density);
                boolean commit = event.getActionMasked() == MotionEvent.ACTION_UP && allowed
                        && GesturePolicy.shouldCommitVertical(dy, getHeight(), threshold);
                callback.onVerticalRelease(dy, getHeight(), commit);
            }
            mode = Mode.NONE;
            longPressActive = false;
            touchSequenceActive = false;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }

    private void cancelPendingLongPress() {
        handler.removeCallbacks(activateLongPress);
    }

    @Override protected void onDetachedFromWindow() {
        cancelPendingLongPress();
        touchSequenceActive = false;
        super.onDetachedFromWindow();
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
