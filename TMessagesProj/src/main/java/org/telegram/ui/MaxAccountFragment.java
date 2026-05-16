package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.max.MaxApiManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class MaxAccountFragment extends BaseFragment {

    private TextView pingStatusText;
    private Button checkConnectionButton;
    private ProgressBar pingProgress;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Max Messenger");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(8), 0, dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // Status banner
        LinearLayout statusCard = makeCard(context);
        TextView statusText = new TextView(context);
        statusText.setText("✓  Резервная доставка активна");
        statusText.setTextSize(15);
        statusText.setTextColor(Color.parseColor("#4CAF50"));
        statusCard.addView(statusText, matchWrap());
        root.addView(statusCard, cardParams());

        // Account info card
        LinearLayout accountCard = makeCard(context);

        TextView accountLabel = makeLabel(context, "Аккаунт");
        accountCard.addView(accountLabel, matchWrap());

        MaxApiManager mgr = MaxApiManager.getInstance();

        TextView phoneText = new TextView(context);
        phoneText.setText(mgr.getPhone().isEmpty() ? "—" : mgr.getPhone());
        phoneText.setTextSize(17);
        phoneText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        LinearLayout.LayoutParams phoneParams = matchWrap();
        phoneParams.topMargin = dp(4);
        accountCard.addView(phoneText, phoneParams);

        long userId = mgr.getFavoritesChatId();
        if (userId != 0) {
            TextView idText = new TextView(context);
            idText.setText("ID: " + userId);
            idText.setTextSize(13);
            idText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            LinearLayout.LayoutParams idParams = matchWrap();
            idParams.topMargin = dp(2);
            accountCard.addView(idText, idParams);
        }

        root.addView(accountCard, cardParams());

        // Connection check card
        LinearLayout connCard = makeCard(context);

        TextView connLabel = makeLabel(context, "Соединение с Max");
        connCard.addView(connLabel, matchWrap());

        pingStatusText = new TextView(context);
        pingStatusText.setText("Не проверялось");
        pingStatusText.setTextSize(15);
        pingStatusText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        LinearLayout.LayoutParams pingTextParams = matchWrap();
        pingTextParams.topMargin = dp(6);
        connCard.addView(pingStatusText, pingTextParams);

        pingProgress = new ProgressBar(context);
        pingProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(10);
        connCard.addView(pingProgress, progressParams);

        checkConnectionButton = new Button(context);
        checkConnectionButton.setText("Проверить соединение");
        checkConnectionButton.setAllCaps(false);
        checkConnectionButton.setTextSize(15);
        checkConnectionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        checkConnectionButton.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams checkBtnParams = matchWrap();
        checkBtnParams.topMargin = dp(4);
        connCard.addView(checkConnectionButton, checkBtnParams);
        checkConnectionButton.setOnClickListener(v -> startPingCheck());

        root.addView(connCard, cardParams());

        // Logout
        Button logoutButton = new Button(context);
        logoutButton.setText("Выйти из Max");
        logoutButton.setAllCaps(false);
        logoutButton.setTextSize(15);
        logoutButton.setTextColor(Color.parseColor("#E53935"));
        logoutButton.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams logoutParams = matchWrap();
        logoutParams.topMargin = dp(16);
        logoutParams.leftMargin = dp(16);
        logoutParams.rightMargin = dp(16);
        root.addView(logoutButton, logoutParams);
        logoutButton.setOnClickListener(v -> confirmLogout());

        fragmentView = scrollView;
        return scrollView;
    }

    private void startPingCheck() {
        checkConnectionButton.setEnabled(false);
        pingProgress.setVisibility(View.VISIBLE);
        pingStatusText.setText("Проверка...");
        pingStatusText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        MaxApiManager.getInstance().checkConnection(new MaxApiManager.PingCallback() {
            @Override
            public void onResult(long pingMs) {
                mainHandler.post(() -> {
                    pingProgress.setVisibility(View.GONE);
                    checkConnectionButton.setEnabled(true);
                    pingStatusText.setText("Соединение установлено · " + pingMs + " мс");
                    pingStatusText.setTextColor(Color.parseColor("#4CAF50"));
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    pingProgress.setVisibility(View.GONE);
                    checkConnectionButton.setEnabled(true);
                    pingStatusText.setText("Ошибка: " + (error != null ? error : "нет соединения"));
                    pingStatusText.setTextColor(Color.parseColor("#E53935"));
                });
            }
        });
    }

    private void confirmLogout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Выйти из Max?");
        builder.setMessage("Резервная доставка через Max будет отключена.");
        builder.setPositiveButton("Выйти", (dialog, which) -> {
            MaxApiManager.getInstance().logout();
            Toast.makeText(getParentActivity(), "Выход из Max выполнен", Toast.LENGTH_SHORT).show();
            finishFragment();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private LinearLayout makeCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        return card;
    }

    private TextView makeLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        return tv;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        return p;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(int v) {
        return AndroidUtilities.dp(v);
    }
}