package cn.kylins.embyshorts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

/** Small code-native design system shared by settings and playback controls. */
public final class UiStyle {
    public static final int BACKGROUND = Color.rgb(10, 14, 22);
    public static final int SURFACE = Color.rgb(22, 28, 40);
    public static final int SURFACE_RAISED = Color.rgb(31, 39, 55);
    public static final int PRIMARY = Color.rgb(124, 156, 255);
    public static final int PRIMARY_DARK = Color.rgb(88, 120, 225);
    public static final int TEXT_PRIMARY = Color.rgb(244, 246, 252);
    public static final int TEXT_SECONDARY = Color.rgb(157, 169, 190);
    public static final int OUTLINE = Color.rgb(58, 70, 91);
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private UiStyle() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static Drawable rounded(Context context, int color, float radiusDp) {
        return rounded(context, color, radiusDp, Color.TRANSPARENT, 0);
    }

    public static Drawable rounded(Context context, int color, float radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    public static Drawable rippleRounded(Context context, int color, float radiusDp) {
        Drawable content = rounded(context, color, radiusDp);
        Drawable mask = rounded(context, Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    public static Drawable rippleCircle(Context context, int color) {
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.OVAL);
        content.setColor(color);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), content, mask);
    }

    public static void stylePrimaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(TEXT_PRIMARY);
        button.setTextSize(15);
        button.setTypeface(MEDIUM);
        button.setLetterSpacing(0.01f);
        button.setBackground(rippleRounded(button.getContext(), PRIMARY_DARK, 14));
        button.setMinHeight(dp(button.getContext(), 50));
        button.setPadding(dp(button.getContext(), 18), 0, dp(button.getContext(), 18), 0);
    }

    public static void styleSecondaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(TEXT_PRIMARY);
        button.setTextSize(14);
        button.setTypeface(MEDIUM);
        button.setBackground(rippleRounded(button.getContext(), SURFACE_RAISED, 12));
        button.setMinHeight(dp(button.getContext(), 46));
        button.setPadding(dp(button.getContext(), 16), 0, dp(button.getContext(), 16), 0);
    }

    public static void styleTextInput(EditText input) {
        Context context = input.getContext();
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SECONDARY);
        input.setTextSize(16);
        input.setTypeface(REGULAR);
        input.setSelectAllOnFocus(false);
        input.setBackground(rounded(context, Color.rgb(15, 20, 30), 12, OUTLINE, 1));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        input.setMinHeight(dp(context, 50));
    }

    public static void styleSeekBar(SeekBar seekBar) {
        seekBar.setProgressTintList(ColorStateList.valueOf(PRIMARY));
        seekBar.setThumbTintList(ColorStateList.valueOf(TEXT_PRIMARY));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(OUTLINE));
        seekBar.setSplitTrack(false);
    }

    public static void styleIconButton(ImageButton button, boolean subtle) {
        button.setBackground(rippleCircle(button.getContext(), subtle ? 0x332F3B53 : SURFACE_RAISED));
        button.setColorFilter(TEXT_PRIMARY);
        button.setPadding(dp(button.getContext(), 11), dp(button.getContext(), 11),
                dp(button.getContext(), 11), dp(button.getContext(), 11));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setElevation(dp(button.getContext(), 1));
    }

    public static void stylePrimaryText(TextView view, float sizeSp, boolean medium) {
        view.setTextColor(TEXT_PRIMARY);
        view.setTextSize(sizeSp);
        view.setTypeface(medium ? MEDIUM : REGULAR);
    }

    public static void styleSecondaryText(TextView view, float sizeSp) {
        view.setTextColor(TEXT_SECONDARY);
        view.setTextSize(sizeSp);
        view.setTypeface(REGULAR);
    }

    public static void setCard(View view) {
        view.setBackground(rounded(view.getContext(), SURFACE, 16, OUTLINE, 1));
        view.setElevation(dp(view.getContext(), 1));
    }
}
