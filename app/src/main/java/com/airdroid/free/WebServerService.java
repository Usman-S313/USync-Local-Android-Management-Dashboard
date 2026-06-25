package com.airdroid.free;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Foreground service that keeps the embedded NanoHTTPD web server alive.
 * Runs visibly with a persistent notification — no covert behavior.
 */
public class WebServerService extends Service {

    public static final String ACTION_STATUS = "com.airdroid.free.STATUS";
    static final int PORT = 8080;
    private static final String TAG = "WebServerService";
    private static final String CHANNEL_ID = "server_service";
    private static final int NOTIF_ID = 1;

    private static volatile boolean running = false;

    private WebServer server;
    private final IBinder binder = new LocalBinder();

    public static boolean isRunning() {
        return running;
    }

    class LocalBinder extends Binder {
        WebServerService getService() {
            return WebServerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }

        if (server == null) {
            try {
                server = new WebServer(this, PORT);
                server.start(60000);
                running = true;
                Log.i(TAG, "Web server started on port " + PORT);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start web server", e);
                running = false;
            }
        }
        broadcastStatus();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        running = false;
        broadcastStatus();
        super.onDestroy();
    }

    private void broadcastStatus() {
        Intent i = new Intent(ACTION_STATUS);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.notif_channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        String ip = NetworkUtils.getLocalIpAddress(this);
        String text = ip == null
                ? "Connect to Wi-Fi to use the dashboard"
                : getString(R.string.notif_text, ip);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_usync)
                .setOngoing(true)
                .build();
    }
}
