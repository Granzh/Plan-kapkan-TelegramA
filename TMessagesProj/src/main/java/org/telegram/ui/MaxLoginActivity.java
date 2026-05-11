package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.max.MaxApiManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class MaxLoginActivity extends BaseFragment {

    private static final int STATE_PHONE = 0;
    private static final int STATE_CODE = 1;

    private int state = STATE_PHONE;
    private String tempToken = "";

    private EditText phoneField;
    private EditText codeField;
    private Button actionButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout phoneLayout;
    private LinearLayout codeLayout;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Войти в Max");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        // Header
        TextView header = new TextView(context);
        header.setText("Подключение к Max Messenger");
        header.setTextSize(18);
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        header.setPadding(0, 0, 0, dp(8));
        root.addView(header, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setText("Сообщения будут отправляться в Избранное Max, когда нет соединения с Telegram.");
        desc.setTextSize(14);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setPadding(0, 0, 0, dp(24));
        root.addView(desc, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Phone layout
        phoneLayout = new LinearLayout(context);
        phoneLayout.setOrientation(LinearLayout.VERTICAL);

        TextView phoneLabel = new TextView(context);
        phoneLabel.setText("Номер телефона (Max)");
        phoneLabel.setTextSize(14);
        phoneLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        phoneLabel.setPadding(0, 0, 0, dp(4));
        phoneLayout.addView(phoneLabel, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        phoneField = new EditText(context);
        phoneField.setHint("+79991234567");
        phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneField.setTextSize(16);
        phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        phoneField.setPadding(0, dp(8), 0, dp(8));
        phoneLayout.addView(phoneField, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(phoneLayout, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Code layout (hidden initially)
        codeLayout = new LinearLayout(context);
        codeLayout.setOrientation(LinearLayout.VERTICAL);
        codeLayout.setVisibility(View.GONE);

        TextView codeLabel = new TextView(context);
        codeLabel.setText("SMS-код из Max");
        codeLabel.setTextSize(14);
        codeLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        codeLabel.setPadding(0, 0, 0, dp(4));
        codeLayout.addView(codeLabel, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        codeField = new EditText(context);
        codeField.setHint("123456");
        codeField.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeField.setTextSize(20);
        codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        codeField.setPadding(0, dp(8), 0, dp(8));
        codeLayout.addView(codeField, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(codeLayout, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Progress + status
        progressBar = new ProgressBar(context);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, progressParams());

        statusText = new TextView(context);
        statusText.setTextSize(13);
        statusText.setTextColor(Color.parseColor("#E53935"));
        statusText.setPadding(0, dp(8), 0, 0);
        statusText.setVisibility(View.GONE);
        root.addView(statusText, params(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Action button
        actionButton = new Button(context);
        actionButton.setText("Получить код");
        actionButton.setTextColor(Color.WHITE);
        actionButton.setBackgroundColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnParams.topMargin = dp(24);
        root.addView(actionButton, btnParams);

        actionButton.setOnClickListener(v -> onActionClicked());

        fragmentView = root;
        return root;
    }

    private void onActionClicked() {
        if (state == STATE_PHONE) {
            String phone = phoneField.getText().toString().trim();
            if (phone.isEmpty()) {
                showError("Введите номер телефона");
                return;
            }
            requestCode(phone);
        } else {
            String code = codeField.getText().toString().trim();
            if (code.length() != 6) {
                showError("Введите 6-значный код");
                return;
            }
            verifyCode(code);
        }
    }

    private void requestCode(String phone) {
        setLoading(true);
        MaxApiManager.getInstance().requestCode(phone, new MaxApiManager.AuthCallback() {
            @Override
            public void onTempToken(String token) {
                tempToken = token;
                mainHandler.post(() -> {
                    setLoading(false);
                    switchToCodeState();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showError(error != null ? error : "Неизвестная ошибка");
                });
            }
        });
    }

    private void verifyCode(String code) {
        setLoading(true);
        MaxApiManager.getInstance().loginWithCode(tempToken, code, new MaxApiManager.LoginCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(getParentActivity(),
                            "Успешно подключено к Max!", Toast.LENGTH_SHORT).show();
                    finishFragment();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showError(error != null ? error : "Неверный код");
                });
            }
        });
    }

    private void switchToCodeState() {
        state = STATE_CODE;
        phoneLayout.setVisibility(View.GONE);
        codeLayout.setVisibility(View.VISIBLE);
        actionButton.setText("Подтвердить");
        clearError();
        codeField.requestFocus();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        actionButton.setEnabled(!loading);
        clearError();
    }

    private void showError(String msg) {
        statusText.setText(msg);
        statusText.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        statusText.setVisibility(View.GONE);
    }

    private static int dp(int dp) {
        return AndroidUtilities.dp(dp);
    }

    private static LinearLayout.LayoutParams params(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private static LinearLayout.LayoutParams progressParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(16);
        return p;
    }
}