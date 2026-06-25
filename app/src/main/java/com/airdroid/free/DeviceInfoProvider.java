package com.airdroid.free;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

import org.json.JSONObject;

/** Provides device specs, battery, and storage info as JSON. */
class DeviceInfoProvider {

    private final Context ctx;

    DeviceInfoProvider(Context ctx) {
        this.ctx = ctx;
    }

    String getDeviceInfo() {
        try {
            JSONObject o = new JSONObject();
            o.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            o.put("brand", Build.BRAND);
            o.put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            o.put("device", Build.DEVICE);

            // Battery
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = ctx.registerReceiver(null, ifilter);
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int pct = (int) (level / (float) scale * 100);
                int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                o.put("battery", pct + "%");
                o.put("charging", plugged != 0);
            }

            // Storage
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long total = stat.getTotalBytes();
            long available = stat.getAvailableBytes();
            o.put("storage_total", humanSize(total));
            o.put("storage_free", humanSize(available));
            o.put("storage_used_pct", (int) ((1f - (available / (float) total)) * 100));

            return o.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + WebServer.escape(e.getMessage()) + "\"}";
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String prefix = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), prefix);
    }
}
