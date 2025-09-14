package com.android.netx;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int SYSTEM_ALERT_WINDOW_PERMISSION = 1002;

    private TextView wifiDownload, wifiUpload;
    private TextView mobileDownload, mobileUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check overlay permission first
        checkPermissions();

        // Set new layout for usage display
        setContentView(R.layout.activity_main);

        wifiDownload = findViewById(R.id.wifi_download);
        wifiUpload = findViewById(R.id.wifi_upload);
        mobileDownload = findViewById(R.id.mobile_download);
        mobileUpload = findViewById(R.id.mobile_upload);

        // Display cumulative usage
        updateUsageDisplay();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivityForResult(intent, SYSTEM_ALERT_WINDOW_PERMISSION);
            } else {
                // Permission already granted, start service
                startNetworkService();
            }
        } else {
            // For older versions, just start service
            startNetworkService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SYSTEM_ALERT_WINDOW_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startNetworkService();
                } else {
                    Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startNetworkService() {
        try {
            Intent serviceIntent = new Intent(this, NetworkMonitorService.class);
            startService(serviceIntent);
            Toast.makeText(this, "NetX is running in background", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("NetX", "Error starting service: " + e.getMessage());
            Toast.makeText(this, "Error starting NetX service", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUsageDisplay() {
        // Update WiFi usage
        wifiDownload.setText("Download: " + NetworkMonitorService.formatBytes(NetworkMonitorService.totalWifiDownload));
        wifiUpload.setText("Upload: " + NetworkMonitorService.formatBytes(NetworkMonitorService.totalWifiUpload));

        // Update Mobile Data usage
        mobileDownload.setText("Download: " + NetworkMonitorService.formatBytes(NetworkMonitorService.totalMobileDownload));
        mobileUpload.setText("Upload: " + NetworkMonitorService.formatBytes(NetworkMonitorService.totalMobileUpload));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh usage display when returning to the activity
        updateUsageDisplay();
    }
}
