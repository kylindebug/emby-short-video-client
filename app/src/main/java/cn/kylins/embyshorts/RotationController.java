package cn.kylins.embyshorts;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.OrientationEventListener;

public final class RotationController {
    private static final int ACCEPT_ANGLE_DEGREES = 18;
    private static final long STABLE_MS = 800;
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OrientationEventListener listener;
    private boolean locked;
    private int pendingOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final Runnable applyPending;

    public RotationController(Activity activity) {
        this.activity = activity;
        applyPending = () -> {
            if (!locked && pendingOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                this.activity.setRequestedOrientation(pendingOrientation);
            }
        };
        listener = new OrientationEventListener(activity) {
            @Override public void onOrientationChanged(int angle) {
                if (locked || angle == ORIENTATION_UNKNOWN) return;
                int target = targetFor(angle);
                if (target == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    handler.removeCallbacks(applyPending);
                    pendingOrientation = target;
                    return;
                }
                if (target != pendingOrientation) {
                    handler.removeCallbacks(applyPending);
                    pendingOrientation = target;
                    handler.postDelayed(applyPending, STABLE_MS);
                }
            }
        };
    }

    public void start() { if (listener.canDetectOrientation()) listener.enable(); }
    public void stop() { listener.disable(); handler.removeCallbacks(applyPending); }
    public boolean isLocked() { return locked; }

    public void toggleLock() {
        locked = !locked;
        handler.removeCallbacks(applyPending);
        if (locked) {
            int orientation = activity.getResources().getConfiguration().orientation;
            activity.setRequestedOrientation(orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    public void toggleManualOrientation() {
        int orientation = activity.getResources().getConfiguration().orientation;
        activity.setRequestedOrientation(orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    static int targetFor(int angle) {
        if (near(angle, 0)) return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (near(angle, 90)) return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        if (near(angle, 180)) return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        if (near(angle, 270)) return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    private static boolean near(int angle, int target) {
        int distance = Math.abs(angle - target);
        distance = Math.min(distance, 360 - distance);
        return distance <= ACCEPT_ANGLE_DEGREES;
    }
}
