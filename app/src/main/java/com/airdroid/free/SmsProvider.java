package com.airdroid.free;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/** Reads recent SMS messages (inbox) as JSON. Read-only. */
class SmsProvider {

    private final Context ctx;

    SmsProvider(Context ctx) {
        this.ctx = ctx;
    }

    String listSms(int limit) {
        if (!hasPermission()) {
            return "{\"error\":\"SMS permission not granted\"}";
        }
        JSONArray arr = new JSONArray();
        Cursor c = null;
        try {
            Uri inbox = Uri.parse("content://sms/inbox");
            c = ctx.getContentResolver().query(
                    inbox,
                    new String[]{"_id", "address", "date", "body"},
                    null, null,
                    "date DESC LIMIT " + Math.max(1, limit));
            if (c != null && c.moveToFirst()) {
                do {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("address", c.getString(1));
                    o.put("date", c.getLong(2));
                    o.put("body", c.getString(3));
                    arr.put(o);
                } while (c.moveToNext());
            }
            JSONObject res = new JSONObject();
            res.put("count", arr.length());
            res.put("messages", arr);
            return res.toString();
        } catch (SecurityException e) {
            return "{\"error\":\"permission denied\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + WebServer.escape(e.getMessage()) + "\"}";
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
