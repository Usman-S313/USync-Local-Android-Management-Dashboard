package com.airdroid.free;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** Small helper for resolving the device's local LAN IP. */
public final class NetworkUtils {

    private NetworkUtils() {}

    public static String getLocalIpAddress(Context context) {
        // Try the Wi-Fi manager first (works on most devices)
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    String s = Formatter.formatIpAddress(ip);
                    if (!s.equals("0.0.0.0")) return s;
                }
            }
        } catch (Exception ignored) {
        }
        // Fallback: enumerate interfaces
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
