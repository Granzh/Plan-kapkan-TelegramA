package org.telegram.messenger.max;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import org.telegram.messenger.ApplicationLoader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaxApiManager {

    private static final String TAG = "MaxApiManager";
    private static final String PREFS_NAME = "max_api_prefs";
    private static final String PREF_PHONE = "phone";
    private static final String PREF_FAVORITES_CHAT_ID = "favorites_chat_id";

    private static volatile MaxApiManager instance;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private long nativeHandle = 0;
    private String phone = "";
    private long favoritesChatId = 0;

    private MaxApiManager() {
        SharedPreferences prefs = prefs();
        phone = prefs.getString(PREF_PHONE, "");
        favoritesChatId = prefs.getLong(PREF_FAVORITES_CHAT_ID, 0);

        if (!phone.isEmpty()) {
            initNative(phone);
        }
    }

    public static MaxApiManager getInstance() {
        if (instance == null) {
            synchronized (MaxApiManager.class) {
                if (instance == null) {
                    instance = new MaxApiManager();
                }
            }
        }
        return instance;
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    /** Returns true if a saved Max session exists. */
    public boolean isAuthenticated() {
        if (nativeHandle == 0) return false;
        try {
            return MaxApiJni.nativeIsAuthenticated(nativeHandle);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library not loaded", e);
            return false;
        }
    }

    /**
     * Start login flow: initialize native client for the given phone, then connect.
     * Callback fires on the calling thread (run this off the main thread).
     */
    public interface AuthCallback {
        void onTempToken(String tempToken);
        void onError(String error);
    }

    public interface LoginCallback {
        void onSuccess();
        void onError(String error);
    }

    /** Step 1: connect + request SMS code for phone. */
    public void requestCode(String phoneNumber, AuthCallback callback) {
        ioExecutor.submit(() -> {
            try {
                savePhone(phoneNumber);
                initNative(phoneNumber);

                boolean connected = MaxApiJni.nativeConnect(nativeHandle);
                if (!connected) {
                    callback.onError("Не удалось подключиться к серверам Max");
                    return;
                }

                String token = MaxApiJni.nativeRequestCode(nativeHandle, phoneNumber);
                if (token.isEmpty()) {
                    callback.onError("Ошибка запроса кода. Проверьте номер телефона.");
                    return;
                }

                callback.onTempToken(token);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    /** Step 2: submit SMS code and save session. */
    public void loginWithCode(String tempToken, String code, LoginCallback callback) {
        ioExecutor.submit(() -> {
            try {
                if (nativeHandle == 0) {
                    callback.onError("Клиент не инициализирован");
                    return;
                }

                boolean ok = MaxApiJni.nativeLoginWithCode(nativeHandle, tempToken, code);
                if (!ok) {
                    callback.onError("Неверный код. Попробуйте ещё раз.");
                    return;
                }

                boolean synced = MaxApiJni.nativeSyncData(nativeHandle);
                if (synced) {
                    long userId = MaxApiJni.nativeGetMyUserId(nativeHandle);
                    saveFavoritesChatIdFromDialogs(userId);
                }

                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    /** Disconnect from Max and clear saved session. */
    public void logout() {
        ioExecutor.submit(() -> {
            if (nativeHandle != 0) {
                MaxApiJni.nativeStop(nativeHandle);
                MaxApiJni.nativeDestroy(nativeHandle);
                nativeHandle = 0;
            }
            prefs().edit()
                    .remove(PREF_PHONE)
                    .remove(PREF_FAVORITES_CHAT_ID)
                    .apply();
            phone = "";
            favoritesChatId = 0;
        });
    }

    // ── Message sending ───────────────────────────────────────────────────

    /**
     * Send a JSON-encoded Telegram message to Max Favorites.
     * Format: {"v":1,"t":"msg","dir":"in","cid":...,"mid":...,"from":"...","body":"...","ts":...}
     * Must be called off the main thread.
     */
    public boolean sendToFavorites(String jsonMessage) {
        if (nativeHandle == 0 || favoritesChatId == 0) {
            Log.w(TAG, "sendToFavorites: not ready (handle=" + nativeHandle + " chatId=" + favoritesChatId + ")");
            return false;
        }
        try {
            ensureConnected();
            return MaxApiJni.nativeSendMessage(nativeHandle, favoritesChatId, jsonMessage);
        } catch (Exception e) {
            Log.e(TAG, "sendToFavorites failed", e);
            return false;
        }
    }

    public long getFavoritesChatId() {
        return favoritesChatId;
    }

    public void setFavoritesChatId(long chatId) {
        favoritesChatId = chatId;
        prefs().edit().putLong(PREF_FAVORITES_CHAT_ID, chatId).apply();
    }

    public String getPhone() {
        return phone;
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void initNative(String phoneNumber) {
        if (nativeHandle != 0) {
            MaxApiJni.nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
        Context ctx = ApplicationLoader.applicationContext;
        String workDir = ctx.getFilesDir().getAbsolutePath() + "/max_session";
        new java.io.File(workDir).mkdirs();
        nativeHandle = MaxApiJni.nativeCreate(phoneNumber, workDir);
        if (nativeHandle == 0) {
            Log.e(TAG, "nativeCreate returned 0 for phone=" + phoneNumber);
        }
    }

    private void ensureConnected() {
        if (nativeHandle != 0 && !MaxApiJni.nativeIsAuthenticated(nativeHandle)) {
            MaxApiJni.nativeConnect(nativeHandle);
        }
    }

    /** After sync_data the self-dialog chatId equals the user's own userId in Max. */
    private void saveFavoritesChatIdFromDialogs(long userId) {
        if (userId != 0) {
            favoritesChatId = userId;
            prefs().edit().putLong(PREF_FAVORITES_CHAT_ID, userId).apply();
        }
    }

    private void savePhone(String phoneNumber) {
        phone = phoneNumber;
        prefs().edit().putString(PREF_PHONE, phoneNumber).apply();
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}