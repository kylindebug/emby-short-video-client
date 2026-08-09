package cn.kylins.embyshorts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends Activity {
    private static final int RADIO_ID_BASE = 100;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<EmbyApi.Folder> folderPath = new ArrayList<>();
    private AppSettings settings;
    private RadioGroup protocolGroup;
    private EditText hostInput;
    private EditText portInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private ImageButton passwordVisibilityButton;
    private boolean passwordVisible;
    private Button folderButton;
    private RadioGroup decoderGroup;
    private SeekBar upperSeek;
    private SeekBar lowerSeek;
    private TextView upperValue;
    private TextView lowerValue;
    private RadioGroup startGroup;
    private RadioGroup endGroup;
    private AlertDialog folderDialog;
    private TextView folderBreadcrumb;
    private TextView folderEmptyText;
    private ListView folderList;
    private Button folderUpButton;
    private Button folderSelectButton;
    private EmbyApi folderApi;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(24), dp(18), dp(36));
        content.setBackgroundColor(UiStyle.BACKGROUND);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = label("Emby短视频客户端", 26);
        UiStyle.stylePrimaryText(title, 28, true);
        title.setLetterSpacing(0.01f);
        content.addView(title);
        TextView subtitle = label("连接媒体库，定制属于你的播放体验", 14);
        UiStyle.styleSecondaryText(subtitle, 14);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(6);
        content.addView(subtitle, subtitleParams);
        content.addView(section("服务器"));

        protocolGroup = radioGroup(new String[]{"HTTP", "HTTPS"}, settings.protocol().equals("https") ? 1 : 0);
        content.addView(field("服务器协议", protocolGroup));
        hostInput = input(settings.host(), "192.168.1.10 或 emby.example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(field("服务器地址", hostInput));
        portInput = input(String.valueOf(settings.port()), "8096", InputType.TYPE_CLASS_NUMBER);
        content.addView(field("端口", portInput));
        usernameInput = input(settings.username(), "Emby 用户名", InputType.TYPE_CLASS_TEXT);
        content.addView(field("用户名", usernameInput));
        content.addView(field("密码（仅保存在应用内）", passwordField()));

        folderButton = new Button(this);
        UiStyle.styleSecondaryButton(folderButton);
        updateFolderButton();
        folderButton.setOnClickListener(v -> chooseFolder());
        content.addView(field("当前播放合集或文件夹", folderButton));

        content.addView(section("播放"));
        decoderGroup = radioGroup(new String[]{"硬解 · MediaCodec", "软解 · LibVLC"}, settings.decoder() == AppSettings.Decoder.HARDWARE ? 0 : 1);
        content.addView(field("解码方式", decoderGroup));

        upperValue = label("", 14);
        upperSeek = speedSeek(settings.upperSpeed(), upperValue);
        content.addView(sliderField("长按上半区倍速", upperSeek, upperValue));
        lowerValue = label("", 14);
        lowerSeek = speedSeek(settings.lowerSpeed(), lowerValue);
        content.addView(sliderField("长按下半区倍速", lowerSeek, lowerValue));

        startGroup = radioGroup(new String[]{"进入后自动播放", "进入后暂停"}, settings.startMode() == AppSettings.StartMode.AUTO_PLAY ? 0 : 1);
        content.addView(field("进入视频", startGroup));
        int endIndex = switch (settings.endMode()) {
            case NEXT -> 0;
            case PAUSE -> 1;
            case LOOP -> 2;
        };
        endGroup = radioGroup(new String[]{"播放下一集", "暂停", "循环本集"}, endIndex);
        content.addView(field("播放结束后", endGroup));

        Button save = new Button(this);
        save.setText("保存并返回播放");
        UiStyle.stylePrimaryButton(save);
        save.setOnClickListener(v -> saveAndFinish());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        saveParams.topMargin = dp(22);
        content.addView(save, saveParams);
        return scroll;
    }

    private View passwordField() {
        FrameLayout box = new FrameLayout(this);
        passwordInput = input(settings.password(), "Emby 密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordInput.setPadding(passwordInput.getPaddingLeft(), passwordInput.getPaddingTop(), dp(56), passwordInput.getPaddingBottom());
        box.addView(passwordInput, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        passwordVisibilityButton = new ImageButton(this);
        passwordVisibilityButton.setImageResource(R.drawable.ic_visibility);
        passwordVisibilityButton.setContentDescription("显示密码");
        UiStyle.styleIconButton(passwordVisibilityButton, true);
        passwordVisibilityButton.setColorFilter(UiStyle.TEXT_SECONDARY);
        passwordVisibilityButton.setOnClickListener(v -> togglePasswordVisibility());
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
        box.addView(passwordVisibilityButton, iconParams);
        return box;
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        int cursor = passwordInput.getSelectionStart();
        passwordInput.setTransformationMethod(passwordVisible
                ? HideReturnsTransformationMethod.getInstance()
                : PasswordTransformationMethod.getInstance());
        passwordVisibilityButton.setImageResource(passwordVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        passwordVisibilityButton.setContentDescription(passwordVisible ? "隐藏密码" : "显示密码");
        passwordInput.setSelection(Math.max(0, Math.min(cursor, passwordInput.length())));
    }

    private void chooseFolder() {
        if (!saveServerFields()) return;
        folderButton.setEnabled(false);
        folderButton.setText("正在连接并读取…");
        io.execute(() -> {
            try {
                EmbyApi api = new EmbyApi(this, settings);
                api.authenticate();
                List<EmbyApi.Folder> roots = api.getFolders("");
                runOnUiThread(() -> showFolderBrowser(api, roots));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    folderButton.setEnabled(true);
                    updateFolderButton();
                    Toast.makeText(this, "连接失败：" + readable(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showFolderBrowser(EmbyApi api, List<EmbyApi.Folder> roots) {
        folderButton.setEnabled(true);
        updateFolderButton();
        folderApi = api;
        folderPath.clear();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(8));
        content.setBackgroundColor(UiStyle.BACKGROUND);
        folderBreadcrumb = label("媒体库", 15);
        folderBreadcrumb.setTextColor(UiStyle.PRIMARY);
        folderBreadcrumb.setTypeface(UiStyle.MEDIUM);
        content.addView(folderBreadcrumb);

        folderEmptyText = label("", 14);
        UiStyle.styleSecondaryText(folderEmptyText, 14);
        folderEmptyText.setPadding(0, dp(8), 0, dp(4));
        content.addView(folderEmptyText);

        folderList = new ListView(this);
        folderList.setDivider(new ColorDrawable(UiStyle.OUTLINE));
        folderList.setDividerHeight(1);
        folderList.setSelector(UiStyle.rippleRounded(this, UiStyle.SURFACE_RAISED, 10));
        content.addView(folderList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        folderList.setOnItemClickListener((parent, view, position, id) -> {
            EmbyApi.Folder child = (EmbyApi.Folder) parent.getItemAtPosition(position);
            folderPath.add(child);
            requestFolderLevel(child.id, () -> {
                if (!folderPath.isEmpty() && folderPath.get(folderPath.size() - 1).id.equals(child.id)) {
                    folderPath.remove(folderPath.size() - 1);
                }
            });
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        folderUpButton = new Button(this);
        folderUpButton.setText("上一级");
        UiStyle.styleSecondaryButton(folderUpButton);
        folderUpButton.setOnClickListener(v -> navigateUp());
        actions.addView(folderUpButton);
        folderSelectButton = new Button(this);
        folderSelectButton.setText("选择当前目录");
        UiStyle.stylePrimaryButton(folderSelectButton);
        folderSelectButton.setOnClickListener(v -> selectCurrentFolder());
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectParams.leftMargin = dp(8);
        actions.addView(folderSelectButton, selectParams);
        content.addView(actions);

        folderDialog = new AlertDialog.Builder(this)
                .setTitle("选择合集或文件夹")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        folderDialog.setOnDismissListener(dialog -> folderApi = null);
        folderDialog.setOnShowListener(dialog -> {
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f);
            folderDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
            folderDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UiStyle.PRIMARY);
            folderDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(UiStyle.MEDIUM);
        });
        folderDialog.show();
        showFolderLevel(roots);
    }

    private void requestFolderLevel(String parentId, Runnable onFailure) {
        EmbyApi api = folderApi;
        if (api == null) return;
        setFolderLoading(true);
        io.execute(() -> {
            try {
                List<EmbyApi.Folder> folders = api.getFolders(parentId);
                runOnUiThread(() -> {
                    if (folderDialog != null && folderDialog.isShowing()) showFolderLevel(folders);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (folderDialog == null || !folderDialog.isShowing()) return;
                    if (onFailure != null) onFailure.run();
                    setFolderLoading(false);
                    updateBreadcrumb();
                    Toast.makeText(this, "无法读取目录：" + readable(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showFolderLevel(List<EmbyApi.Folder> folders) {
        ArrayAdapter<EmbyApi.Folder> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, folders);
        folderList.setAdapter(adapter);
        if (folders.isEmpty()) {
            folderEmptyText.setText(folderPath.isEmpty()
                    ? "服务器没有可用媒体库"
                    : "此目录没有子目录，可点击下方按钮选择当前目录");
        } else {
            folderEmptyText.setText("点击目录名称进入下一级");
        }
        setFolderLoading(false);
        updateBreadcrumb();
    }

    private void navigateUp() {
        if (folderPath.isEmpty()) return;
        EmbyApi.Folder removed = folderPath.remove(folderPath.size() - 1);
        String parentId = folderPath.isEmpty() ? "" : folderPath.get(folderPath.size() - 1).id;
        requestFolderLevel(parentId, () -> folderPath.add(removed));
    }

    private void selectCurrentFolder() {
        if (folderPath.isEmpty()) return;
        EmbyApi.Folder current = folderPath.get(folderPath.size() - 1);
        settings.saveFolder(current.id, selectedPathText());
        updateFolderButton();
        folderDialog.dismiss();
    }

    private void setFolderLoading(boolean loading) {
        if (folderList == null) return;
        folderList.setEnabled(!loading);
        folderUpButton.setEnabled(!loading && !folderPath.isEmpty());
        folderSelectButton.setEnabled(!loading && !folderPath.isEmpty());
        if (loading) folderEmptyText.setText("正在读取目录…");
    }

    private void updateBreadcrumb() {
        StringBuilder text = new StringBuilder("媒体库");
        for (EmbyApi.Folder folder : folderPath) text.append("  ›  ").append(folder.name);
        folderBreadcrumb.setText(text);
        folderUpButton.setEnabled(!folderPath.isEmpty());
        folderSelectButton.setEnabled(!folderPath.isEmpty());
    }

    private String selectedPathText() {
        StringBuilder text = new StringBuilder();
        for (EmbyApi.Folder folder : folderPath) {
            if (text.length() > 0) text.append(" / ");
            text.append(folder.name);
        }
        return text.toString();
    }

    private void saveAndFinish() {
        if (!saveServerFields()) return;
        AppSettings.Decoder decoder = decoderGroup.getCheckedRadioButtonId() == RADIO_ID_BASE
                ? AppSettings.Decoder.HARDWARE : AppSettings.Decoder.SOFTWARE;
        AppSettings.StartMode start = startGroup.getCheckedRadioButtonId() == RADIO_ID_BASE
                ? AppSettings.StartMode.AUTO_PLAY : AppSettings.StartMode.PAUSED;
        AppSettings.EndMode end = switch (endGroup.getCheckedRadioButtonId()) {
            case RADIO_ID_BASE -> AppSettings.EndMode.NEXT;
            case RADIO_ID_BASE + 1 -> AppSettings.EndMode.PAUSE;
            default -> AppSettings.EndMode.LOOP;
        };
        settings.savePlayback(decoder, speedForProgress(upperSeek.getProgress()), speedForProgress(lowerSeek.getProgress()), start, end);
        setResult(RESULT_OK);
        finish();
    }

    private boolean saveServerFields() {
        String host = hostInput.getText().toString().trim();
        if (host.startsWith("http://")) host = host.substring(7);
        if (host.startsWith("https://")) host = host.substring(8);
        while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty()) {
            hostInput.setError("请填写服务器地址");
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            portInput.setError("端口应为 1–65535");
            return false;
        }
        String protocol = protocolGroup.getCheckedRadioButtonId() == RADIO_ID_BASE ? "http" : "https";
        settings.saveServer(protocol, host, port, usernameInput.getText().toString(), passwordInput.getText().toString());
        return true;
    }

    private SeekBar speedSeek(float value, TextView valueView) {
        SeekBar seek = new SeekBar(this);
        seek.setMax(63);
        UiStyle.styleSeekBar(seek);
        seek.setProgress(Math.max(0, Math.min(63, Math.round(value * 8f) - 1)));
        updateSpeedLabel(valueView, speedForProgress(seek.getProgress()));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { updateSpeedLabel(valueView, speedForProgress(progress)); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        return seek;
    }

    private View sliderField(String name, SeekBar seek, TextView value) {
        LinearLayout box = verticalBox();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(name, 15), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(value);
        box.addView(header);
        box.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View field(String name, View input) {
        LinearLayout box = verticalBox();
        TextView fieldLabel = label(name, 14);
        UiStyle.styleSecondaryText(fieldLabel, 13);
        fieldLabel.setTypeface(UiStyle.MEDIUM);
        box.addView(fieldLabel);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(9);
        box.addView(input, params);
        return box;
    }

    private LinearLayout verticalBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        UiStyle.setCard(box);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(9);
        box.setLayoutParams(params);
        return box;
    }

    private RadioGroup radioGroup(String[] texts, int checkedIndex) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < texts.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(RADIO_ID_BASE + i);
            button.setText(texts[i]);
            button.setTextSize(14);
            button.setTextColor(UiStyle.TEXT_PRIMARY);
            button.setTypeface(UiStyle.REGULAR);
            button.setSingleLine(true);
            button.setMaxLines(1);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
            button.setMinWidth(0);
            button.setPadding(0, 0, dp(4), 0);
            button.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{UiStyle.PRIMARY, UiStyle.TEXT_SECONDARY}));
            group.addView(button, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        group.check(RADIO_ID_BASE + checkedIndex);
        return group;
    }

    private EditText input(String value, String hint, int type) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setInputType(type);
        input.setSingleLine(true);
        UiStyle.styleTextInput(input);
        return input;
    }

    private TextView section(String text) {
        TextView view = label(text, 19);
        view.setTextColor(UiStyle.PRIMARY);
        view.setTypeface(UiStyle.MEDIUM);
        view.setLetterSpacing(0.02f);
        view.setPadding(dp(2), dp(28), 0, dp(2));
        return view;
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(UiStyle.TEXT_PRIMARY);
        view.setTypeface(UiStyle.REGULAR);
        return view;
    }

    private void updateFolderButton() {
        folderButton.setText(settings.folderId().isEmpty() ? "连接服务器并选择…" : settings.folderName());
    }

    private static float speedForProgress(int progress) { return (progress + 1) / 8f; }
    private static void updateSpeedLabel(TextView view, float speed) { view.setText(String.format(Locale.US, "%.3g×", speed)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String readable(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (folderDialog != null) folderDialog.dismiss();
        io.shutdownNow();
    }
}
