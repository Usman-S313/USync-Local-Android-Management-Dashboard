package com.airdroid.free;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mirrors notifications to the dashboard. This service is bound by the system
 * and only runs after the user grants access via Settings (the activity
 * provides a button for that). No covert activation — Android enforces that.
 */
public class NotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        NotificationStore.get().add(toJson(sbn, true));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // optional: could mark as dismissed
    }

    private String toJson(StatusBarNotification sbn, boolean posted) {
        try {
            JSONObject o = new JSONObject();
            o.put("package", sbn.getPackageName());
            o.put("posted", posted);
            o.put("time", System.currentTimeMillis());
            o.put("timeText", new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date()));
            CharSequence ticker = sbn.getNotification().tickerText;
            if (ticker != null) o.put("ticker", ticker.toString());
            // Extract visible title/text from notification extras (API 18+)
            String title = "";
            String body = "";
            try {
                android.os.Bundle extras = sbn.getNotification().extras;
                CharSequence t = extras.getCharSequence("android.title");
                CharSequence b = extras.getCharSequence("android.text");
                if (t != null) title = t.toString();
                if (b != null) body = b.toString();
            } catch (Exception ignored) {
            }
            o.put("title", title.isEmpty() ? (ticker != null ? ticker.toString() : "") : title);
            o.put("body", body);
            return o.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + WebServer.escape(e.getMessage()) + "\"}";
        }
    }
}
