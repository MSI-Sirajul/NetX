package com.android.netx;

import android.app.Activity; // Changed to native Activity
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity { // Changed to native Activity

    private Switch lockOverlayToggle, autoHideToggle, disableOverlayToggle;
    private TextView wifiUsage, dataUsage, currentDownload, currentUpload, currentStatus;
    private ImageView profileBt, githubBt;

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    private static final String TAG = "MainActivity";

    private BroadcastReceiver usageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String wifi = intent.getStringExtra("wifi_usage");
            String data = intent.getStringExtra("data_usage");
            String download = intent.getStringExtra("download_speed");
            String upload = intent.getStringExtra("upload_speed");
            String status = intent.getStringExtra("network_status");

            if (wifi != null) wifiUsage.setText(wifi);
            if (data != null) dataUsage.setText(data);
            if (download != null) currentDownload.setText(download + "/s");
            if (upload != null) currentUpload.setText(upload + "/s");

            if (status != null) {
                currentStatus.setText(status);
                // Using direct getResources().getColor() for native Android
                if (status.equals("Disconnected") || status.equals("None")) {
                    currentStatus.setTextColor(getColorCompat(context, R.color.warning));
                } else {
                    currentStatus.setTextColor(getColorCompat(context, R.color.success));
                }
            } else {
                 updateNetworkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "MainActivity onCreate");

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        editor = prefs.edit();

        lockOverlayToggle = findViewById(R.id.lockOverlayToggle);
        autoHideToggle = findViewById(R.id.autoHideToggle);
        disableOverlayToggle = findViewById(R.id.disableOverlayToggle);
        wifiUsage = findViewById(R.id.wifiUsage);
        dataUsage = findViewById(R.id.dataUsage);
        currentDownload = findViewById(R.id.currentDownload);
        currentUpload = findViewById(R.id.currentUpload);
        currentStatus = findViewById(R.id.currentStatus);
        profileBt = findViewById(R.id.profileBt);
        githubBt = findViewById(R.id.githubBt);

        lockOverlayToggle.setChecked(prefs.getBoolean("lock_overlay", false));
        autoHideToggle.setChecked(prefs.getBoolean("auto_hide", false));
        disableOverlayToggle.setChecked(prefs.getBoolean("disable_overlay", false));

        lockOverlayToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("lock_overlay", isChecked).apply();
                Log.d(TAG, "Lock Overlay: " + isChecked);
            }
        });

        autoHideToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("auto_hide", isChecked).apply();
                Log.d(TAG, "Auto Hide: " + isChecked);
            }
        });

        disableOverlayToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("disable_overlay", isChecked).apply();
                Log.d(TAG, "Disable Overlay: " + isChecked);
            }
        });

        profileBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://md-sirajul-islam.vercel.app");
            }
        });

        githubBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://github.com/MSI-Sirajul");
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                startServiceWithCheck();
            }
        } else {
            startServiceWithCheck();
        }

        updateNetworkStatus();
    }

    private void startServiceWithCheck() {
        Intent serviceIntent = new Intent(this, NetworkMonitorService.class);
        // For Android O and above, starting a background service can be restricted.
        // It's recommended to start it as a foreground service, which is handled inside NetworkMonitorService.
        // Calling startService will implicitly call startForegroundService if on O+ and service calls startForeground.
        startService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity onResume");
        registerReceiver(usageReceiver, new IntentFilter("USAGE_UPDATE"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            startServiceWithCheck();
        }
        updateNetworkStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "MainActivity onPause");
        unregisterReceiver(usageReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");
    }

    private void updateNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNet = cm != null ? cm.getActiveNetworkInfo() : null;
        if (activeNet != null && activeNet.isConnected()) {
            currentStatus.setText("Connected");
            currentStatus.setTextColor(getColorCompat(this, R.color.success));
        } else {
            currentStatus.setText("Disconnected");
            currentStatus.setTextColor(getColorCompat(this, R.color.warning));
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening URL: " + e.getMessage());
        }
    }

    // Custom getColorCompat method to avoid ContextCompat dependency
    private int getColorCompat(Context context, int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.getColor(resId);
        } else {
            return context.getResources().getColor(resId);
        }
    }
}