package cn.kylins.embyshorts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
    }

    @Override protected void onResume() {
        super.onResume();
        AppSettings settings = new AppSettings(this);
        if (settings.canPlay()) {
            startActivity(new Intent(this, PlayerActivity.class));
            finish();
            return;
        }
        showEmptyState();
    }

    private void showEmptyState() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        layout.setBackgroundColor(UiStyle.BACKGROUND);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(84), dp(84));
        logoParams.bottomMargin = dp(24);
        layout.addView(logo, logoParams);

        TextView text = new TextView(this);
        text.setText(R.string.empty_title);
        UiStyle.stylePrimaryText(text, 24, true);
        text.setGravity(Gravity.CENTER);
        layout.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText(R.string.empty_hint);
        UiStyle.styleSecondaryText(hint, 15);
        hint.setGravity(Gravity.CENTER);
        hint.setMaxWidth(dp(320));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(10);
        layout.addView(hint, hintParams);

        Button settings = new Button(this);
        settings.setText(R.string.connect_emby);
        UiStyle.stylePrimaryButton(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(210), dp(52));
        params.topMargin = dp(30);
        layout.addView(settings, params);
        setContentView(layout);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
