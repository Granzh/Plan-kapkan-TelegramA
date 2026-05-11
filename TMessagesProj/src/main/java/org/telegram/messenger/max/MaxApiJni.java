package org.telegram.messenger.max;

public class MaxApiJni {

    static {
        System.loadLibrary("max_api_kernel");
    }

    /** Create native client. Returns native pointer (stored as long), 0 on failure. */
    public static native long nativeCreate(String phone, String workDir);

    /** Free native handle. */
    public static native void nativeDestroy(long ptr);

    /** Connect WebSocket + handshake. Must be called before auth operations. */
    public static native boolean nativeConnect(long ptr);

    /** True if a saved auth token exists on disk (no network needed). */
    public static native boolean nativeIsAuthenticated(long ptr);

    /** Request SMS code. Returns temp token, empty string on error. */
    public static native String nativeRequestCode(long ptr, String phone);

    /** Verify SMS code, save session. Returns true on success. */
    public static native boolean nativeLoginWithCode(long ptr, String token, String code);

    /** Sync chats/me info after login. Returns true on success. */
    public static native boolean nativeSyncData(long ptr);

    /** Get authenticated user's Max ID. Returns 0 if not available. */
    public static native long nativeGetMyUserId(long ptr);

    /** Send text message to chatId. Returns true on success. */
    public static native boolean nativeSendMessage(long ptr, long chatId, String text);

    /** Compute chatId for a 1-on-1 dialog (XOR of two user IDs). */
    public static native long nativeGetChatId(long ptr, long firstUserId, long secondUserId);

    /** Stop background event loop. */
    public static native void nativeStop(long ptr);
}