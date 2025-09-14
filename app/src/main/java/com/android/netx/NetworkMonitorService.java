package com.android.netx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class NetworkMonitorService extends Service {

    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "netx_channel";

    private ImageView networkIcon;
    private TextView downloadSpeedText;
    private TextView uploadSpeedText;

    private Handler handler;
    private Runnable speedRunnable;

    private long previousRxBytes = 0;
    private long previousTxBytes = 0;
    private long previousTime = 0;

    private View overlayView;
    private WindowManager windowManager;

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;

    // Cumulative usage totals (static for access from MainActivity)
    public static long totalWifiDownload = 0;
    public static long totalWifiUpload = 0;
    public static long totalMobileDownload = 0;
    public static long totalMobileUpload = 0;

    // Network type constants
    private static final int NETWORK_TYPE_WIFI = 1;
    private static final int NETWORK_TYPE_MOBILE = 2;
    private static final int NETWORK_TYPE_NONE = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("NetX", "NetworkMonitorService created");

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic)
                .setContentTitle("NetX")
                .setContentText("Speed: 0 KB/s | WiFi: 0/0 MB | Data: 0/0 MB")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        initializeOverlay();
        startSpeedMonitoring();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "NetX Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Network speed monitoring service");
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeOverlay() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

            int overlayType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                overlayType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 0;
            params.y = 50;

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.overlay_layout, null);

            networkIcon = overlayView.findViewById(R.id.network_icon);
            downloadSpeedText = overlayView.findViewById(R.id.download_speed);
            uploadSpeedText = overlayView.findViewById(R.id.upload_speed);

            downloadSpeedText.setText("0 KB/s");
            uploadSpeedText.setText("0 KB/s");
            setNetworkIcon(NETWORK_TYPE_NONE, 0);

            windowManager.addView(overlayView, params);

        } catch (Exception e) {
            Log.e("NetX", "Error creating overlay: " + e.getMessage());
        }
    }

    private void removeOverlayView() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
            }
        } catch (Exception e) {
            Log.e("NetX", "Error removing overlay: " + e.getMessage());
        }
    }

    private void startSpeedMonitoring() {
        handler = new Handler(Looper.getMainLooper());
        previousRxBytes = TrafficStats.getTotalRxBytes();
        previousTxBytes = TrafficStats.getTotalTxBytes();
        previousTime = System.currentTimeMillis();

        speedRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    long currentRxBytes = TrafficStats.getTotalRxBytes();
                    long currentTxBytes = TrafficStats.getTotalTxBytes();
                    long currentTime = System.currentTimeMillis();

                    long timeDiff = currentTime - previousTime;
                    if (timeDiff > 0) {
                        long downloadSpeed = (currentRxBytes - previousRxBytes) * 1000 / timeDiff;
                        long uploadSpeed = (currentTxBytes - previousTxBytes) * 1000 / timeDiff;

                        int networkType = getNetworkType();

                        if (networkType == NETWORK_TYPE_WIFI) {
                            totalWifiDownload += (currentRxBytes - previousRxBytes);
                            totalWifiUpload += (currentTxBytes - previousTxBytes);
                        } else if (networkType == NETWORK_TYPE_MOBILE) {
                            totalMobileDownload += (currentRxBytes - previousRxBytes);
                            totalMobileUpload += (currentTxBytes - previousTxBytes);
                        }

                        updateNetworkStatus(networkType, downloadSpeed);

                        if (downloadSpeedText != null) {
                            downloadSpeedText.setText(formatSpeed(downloadSpeed));
                        }
                        if (uploadSpeedText != null) {
                            uploadSpeedText.setText(formatSpeed(uploadSpeed));
                        }

                        // Update notification dynamically
                        String notifText = "Speed: " + formatSpeed(downloadSpeed) + " | " + formatSpeed(uploadSpeed)
                                + "\nWiFi: " + formatBytes(totalWifiDownload) + "/" + formatBytes(totalWifiUpload)
                                + "\nData: " + formatBytes(totalMobileDownload) + "/" + formatBytes(totalMobileUpload);

                        notificationBuilder.setContentText(notifText);
                        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());

                        previousRxBytes = currentRxBytes;
                        previousTxBytes = currentTxBytes;
                        previousTime = currentTime;
                    }
                } catch (Exception e) {
                    Log.e("NetX", "Error in speed monitoring: " + e.getMessage());
                }

                if (handler != null) {
                    handler.postDelayed(this, 1000);
                }
            }
        };

        handler.post(speedRunnable);
    }

    private int getNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork == null || !activeNetwork.isConnected()) {
                return NETWORK_TYPE_NONE;
            }

            int type = activeNetwork.getType();
            if (type == ConnectivityManager.TYPE_WIFI || type == ConnectivityManager.TYPE_ETHERNET) {
                return NETWORK_TYPE_WIFI;
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                return NETWORK_TYPE_MOBILE;
            }
        } catch (Exception e) {
            Log.e("NetX", "Error getting network type: " + e.getMessage());
        }

        return NETWORK_TYPE_NONE;
    }

    private String formatSpeed(long speed) {
        if (speed < 1024 * 1024) return String.format("%.1f KB/s", speed / 1024.0);
        else return String.format("%.1f MB/s", speed / (1024.0 * 1024.0));
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void setNetworkIcon(int networkType, long downloadSpeed) {
        if (networkIcon == null) return;

        int iconResource;

        switch (networkType) {
            case NETWORK_TYPE_WIFI:
                if (downloadSpeed > 5 * 1024 * 1024) iconResource = R.drawable.wifi_strong;
                else if (downloadSpeed > 512 * 1024) iconResource = R.drawable.wifi_slow;
                else iconResource = R.drawable.wifi_week;
                break;

            case NETWORK_TYPE_MOBILE:
                if (downloadSpeed > 5 * 1024 * 1024) iconResource = R.drawable.lte_strong;
                else if (downloadSpeed > 512 * 1024) iconResource = R.drawable.lte_slow;
                else iconResource = R.drawable.lte_week;
                break;

            default:
                iconResource = R.drawable.wifi_week;
                break;
        }

        networkIcon.setImageResource(iconResource);
    }

    private void updateNetworkStatus(int networkType, long downloadSpeed) {
        try {
            setNetworkIcon(networkType, downloadSpeed);
        } catch (Exception e) {
            Log.e("NetX", "Error updating network status: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartService = new Intent(getApplicationContext(), this.getClass());
        restartService.setPackage(getPackageName());
        startService(restartService);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlayView();
        if (handler != null && speedRunnable != null) handler.removeCallbacks(speedRunnable);

        Intent restartService = new Intent(getApplicationContext(), this.getClass());
        restartService.setPackage(getPackageName());
        startService(restartService);
    }
}
