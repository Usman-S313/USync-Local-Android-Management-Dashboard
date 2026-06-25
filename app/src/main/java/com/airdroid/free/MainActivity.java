package com.airdroid.free;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Visible launcher activity. Shows the server URL and lets the user start/stop
 * the embedded web server. No covert behavior — the app is launched normally,
 * shows a notification while running, and only exposes data on the local Wi-Fi.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "usync_prefs";
    private static final String KEY_FIRST_RUN = "first_run_done";

    private TextView statusText, addressText, portText;
    private View statusDot;
    private Button btnStart, btnStop, btnOpen;
    private View btnCopyAddress;

    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bound = true;
            updateUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    // Runtime permission launcher (storage, location, sms)
    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Permissions granted or denied — UI updates on next onResume
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            // If layout inflates badly, show a fallback
            Toast.makeText(this, "Layout error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            statusText = findViewById(R.id.statusText);
            statusDot = findViewById(R.id.statusDot);
            addressText = findViewById(R.id.addressText);
            portText = findViewById(R.id.portText);
            btnStart = findViewById(R.id.btnStart);
            btnStop = findViewById(R.id.btnStop);
            btnOpen = findViewById(R.id.btnOpen);
            btnCopyAddress = findViewById(R.id.btnCopyAddress);

            Button btnPermStorage = findViewById(R.id.btnPermStorage);
            Button btnPermLocation = findViewById(R.id.btnPermLocation);
            Button btnPermSms = findViewById(R.id.btnPermSms);
            Button btnPermNotif = findViewById(R.id.btnPermNotif);

            btnStart.setOnClickListener(v -> startServer());
            btnStop.setOnClickListener(v -> stopServer());
            btnOpen.setOnClickListener(v -> openDashboard());
            btnCopyAddress.setOnClickListener(v -> copyAddressToClipboard());

            btnPermStorage.setOnClickListener(v -> requestStoragePerms());
            btnPermLocation.setOnClickListener(v -> requestLocationPerm());
            btnPermSms.setOnClickListener(v -> requestSmsPerm());
            btnPermNotif.setOnClickListener(v -> openNotifAccessSettings());
        } catch (Exception e) {
            Toast.makeText(this, "Setup error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        showFirstRunHintIfNeeded();
    }

    private void showFirstRunHintIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_FIRST_RUN, false)) {
            prefs.edit().putBoolean(KEY_FIRST_RUN, true).apply();
            Toast.makeText(this, "Grant the permissions below, then press Start Server", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Server control ----------

    private void startServer() {
        if (getLocalIp() == null) {
            Toast.makeText(this, R.string.no_wifi, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, WebServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void stopServer() {
        if (bound) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // service wasn't bound, that's ok
            }
            bound = false;
        }
        stopService(new Intent(this, WebServerService.class));
        updateUi();
    }

    private void openDashboard() {
        String ip = getLocalIp();
        if (ip == null) {
            Toast.makeText(this, R.string.no_wifi, Toast.LENGTH_LONG).show();
            return;
        }
        String url = "http://" + ip + ":8080/";
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void copyAddressToClipboard() {
        String address = addressText.getText().toString();
        if (address.equals("—") || address.isEmpty()) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("USync Address", address);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Server address copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Permissions ----------

    private void requestStoragePerms() {
        // 1. First, always show the standard system pop-up for READ permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            });
        } else {
            permLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        }

        // 2. For Android 11+, if they haven't granted "All Files Access", take them to settings
        // This is REQUIRED to fix the "EPERM (Operation not permitted)" error when uploading.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Grant 'All Files Access' to fix Upload/Export errors", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }
    }

    private void requestLocationPerm() {
        permLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void requestSmsPerm() {
        showRestrictedSettingsInfo();
        permLauncher.launch(new String[]{
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS
        });
    }

    private void openNotifAccessSettings() {
        showRestrictedSettingsInfo();
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void showRestrictedSettingsInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Restricted Setting?")
                    .setMessage("If Android blocks this, please:\n\n1. Go to Phone Settings > Apps > USync\n2. Tap the 3 dots (top right)\n3. Tap 'Allow restricted settings'\n4. Now come back and try again.")
                    .setPositiveButton("I Understand", null)
                    .show();
        }
    }

    // ---------- UI state ----------

    @Override
    protected void onResume() {
        super.onResume();
        syncClipboard();
        registerStatusReceiver();
        updateUi();
    }

    private void syncClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null) {
                    ClipboardProvider.updateCache(text.toString());
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(statusReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // Removed onStart/onStop bind calls — binding is only done in startServer()
    // to avoid double-bind crashes when the server isn't running yet.

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WebServerService.ACTION_STATUS.equals(intent.getAction())) {
                updateUi();
            }
        }
    };

    private void registerStatusReceiver() {
        try {
            IntentFilter f = new IntentFilter(WebServerService.ACTION_STATUS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(statusReceiver, f);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateUi() {
        boolean running = WebServerService.isRunning();
        String ip = getLocalIp();

        if (running && ip != null) {
            statusText.setText(R.string.status_running);
            statusDot.setBackgroundResource(R.drawable.dot_green);
            addressText.setText("http://" + ip + ":8080");
            addressText.setSelected(true); // Start marquee if text is too long
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            btnOpen.setEnabled(true);
            if (portText != null) portText.setVisibility(View.GONE); // Hide duplicate 8080
        } else {
            statusText.setText(R.string.status_stopped);
            statusDot.setBackgroundResource(R.drawable.dot_red);
            addressText.setText(ip == null ? "—" : ip + " (server stopped)");
            addressText.setSelected(false);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            btnOpen.setEnabled(false);
            if (portText != null) portText.setVisibility(View.VISIBLE);
        }
    }

    // ---------- Network helpers ----------

    private String getLocalIp() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isLoopback()) continue;
                if (!iface.isUp()) continue;
                for (java.net.InetAddress addr : Collections.list(iface.getInetAddresses())) {
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
