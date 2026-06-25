package com.airdroid.free;

import android.content.ClipboardManager;
import android.content.Context;
import org.json.JSONObject;

/**
 * Reads the current clipboard content.
 * Note: Since Android 10, apps cannot read clipboard in the background.
 * We cache the last seen clipboard value while the app is in foreground.
 */
class ClipboardProvider {

    private static String cachedText = "";

    private final Context ctx;

    ClipboardProvider(Context ctx) {
        this.ctx = ctx;
    }

    /** Called from MainActivity.onResume to capture the clipboard while focused. */
    static void updateCache(String text) {
        if (text != null) {
            cachedText = text;
        }
    }

    String get() {
        try {
            // Attempt a fresh read first (works if activity is foreground or IME)
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            
            String currentText = "";
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    currentText = text.toString();
                    cachedText = currentText; // Update cache with fresh read
                }
            } else {
                currentText = cachedText; // Fallback to last known value
            }

            JSONObject o = new JSONObject();
            o.put("text", currentText);
            return o.toString();
        } catch (Exception e) {
            // If background access is denied, return the cached value
            try {
                JSONObject o = new JSONObject();
                o.put("text", cachedText);
                return o.toString();
            } catch (Exception ex) {
                return "{\"error\":\"" + WebServer.escape(e.getMessage()) + "\"}";
            }
        }
    }
}
