package com.android.netx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.Locale;

public class NetworkMonitorService extends Service {

    private static final String CHANNEL_ID = "netx_channel";
    private static final int NOTIFICATION_ID = 101;
    private static final String TAG = "NetworkMonitorService";

    private WindowManager windowManager;
    private View overlayView;
    private TextView textDownload, textUpload;
    private ImageView iconOverlay;

    private RemoteViews notificationView;
    private NotificationManager notificationManager;
    private Notification.Builder notificationBuilder; // Using Android's native Notification.Builder

    private Handler handler;
    private Runnable updateRunnable;

    private long lastRxBytes = 0, lastTxBytes = 0;
    private long totalWifiBytes = 0, totalDataBytes = 0;

    private SharedPreferences prefs;

    private boolean overlayLocked = false;
    private boolean autoHideOverlay = false;
    private boolean disableOverlay = false;

    private WindowManager.LayoutParams overlayParams;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotificationChannel();
        setupNotificationBuilder();
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();

        totalWifiBytes = prefs.getLong("total_wifi_bytes", 0);
        totalDataBytes = prefs.getLong("total_data_bytes", 0);

        handler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateStates();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateRunnable);
    }

    private void setupNotificationBuilder() {
        notificationView = new RemoteViews(getPackageName(), R.layout.notification_layout);

        Intent intent = new Intent(this, MainActivity.class);
        // FLAG_IMMUTABLE is a framework flag, not androidx specific.
        // Required for API 31+
        int pendingIntentFlags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        } else {
            pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags);

        notificationBuilder = new Notification.Builder(this, CHANNEL_ID) // Using native Notification.Builder
                .setSmallIcon(R.drawable.lte_slow)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCustomContentView(notificationView)
                .setPriority(Notification.PRIORITY_LOW); // Using native Notification.PRIORITY_LOW
    }

    private void createOverlayView() {
        if (overlayView == null) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.overlay_layout, null);
            textDownload = overlayView.findViewById(R.id.download_speed);
            textUpload = overlayView.findViewById(R.id.upload_speed);
            iconOverlay = overlayView.findViewById(R.id.overlay_icon);

            int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;

            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);

            overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
            overlayParams.x = prefs.getInt("overlay_pos_x", 0);
            overlayParams.y = prefs.getInt("overlay_pos_y", 0);

            overlayView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (overlayLocked && event.getAction() != MotionEvent.ACTION_UP) return true;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = overlayParams.x;
                            initialY = overlayParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            overlayParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                            overlayParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                            if (overlayView.isAttachedToWindow()) {
                                windowManager.updateViewLayout(overlayView, overlayParams);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            prefs.edit()
                                    .putInt("overlay_pos_x", overlayParams.x)
                                    .putInt("overlay_pos_y", overlayParams.y)
                                    .apply();
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    private void addOverlay() {
        if (overlayView == null) {
            createOverlayView();
        }
        if (overlayView != null && !overlayView.isAttachedToWindow()) {
            try {
                windowManager.addView(overlayView, overlayParams);
                Log.d(TAG, "Overlay added to window.");
            } catch (WindowManager.BadTokenException e) {
                Log.e(TAG, "BadTokenException when adding overlay: " + e.getMessage());
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException when adding overlay: " + e.getMessage());
            }
        }
    }

    private void removeOverlay() {
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            try {
                windowManager.removeView(overlayView);
                Log.d(TAG, "Overlay removed from window.");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "IllegalArgumentException when removing overlay: " + e.getMessage());
            } catch (IllegalStateException e) {
                 Log.e(TAG, "IllegalStateException when removing overlay: " + e.getMessage());
            }
        }
    }

    private void updateStates() {
        overlayLocked = prefs.getBoolean("lock_overlay", false);
        autoHideOverlay = prefs.getBoolean("auto_hide", false);
        disableOverlay = prefs.getBoolean("disable_overlay", false);

        long currentRxBytes = TrafficStats.getTotalRxBytes();
        long currentTxBytes = TrafficStats.getTotalTxBytes();

        long downloadSpeed = currentRxBytes - lastRxBytes;
        long uploadSpeed = currentTxBytes - lastTxBytes;

        lastRxBytes = currentRxBytes;
        lastTxBytes = currentTxBytes;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNet = cm != null ? cm.getActiveNetworkInfo() : null;
        boolean connected = activeNet != null && activeNet.isConnected();

        int iconRes;
        String connectionType = "None";

        if (connected) {
            if (activeNet.getType() == ConnectivityManager.TYPE_WIFI) {
                iconRes = R.drawable.wifi_strong;
                totalWifiBytes += (downloadSpeed + uploadSpeed);
                connectionType = "WiFi";
            } else if (activeNet.getType() == ConnectivityManager.TYPE_MOBILE) {
                iconRes = R.drawable.lte_strong;
                totalDataBytes += (downloadSpeed + uploadSpeed);
                connectionType = "Mobile Data";
            } else {
                iconRes = R.drawable.no_network;
            }
        } else {
            iconRes = R.drawable.no_network;
            downloadSpeed = 0;
            uploadSpeed = 0;
            connectionType = "Disconnected";
        }
        
        prefs.edit()
             .putLong("total_wifi_bytes", totalWifiBytes)
             .putLong("total_data_bytes", totalDataBytes)
             .apply();

        if (disableOverlay) {
            removeOverlay();
        } else if (autoHideOverlay) {
            if (connected) {
                addOverlay();
            } else {
                removeOverlay();
            }
        } else {
            addOverlay();
        }

        if (overlayView != null && overlayView.isAttachedToWindow()) {
            textDownload.setText(formatSpeed(downloadSpeed));
            textUpload.setText(formatSpeed(uploadSpeed));
            iconOverlay.setImageResource(iconRes);
        }

        notificationView.setImageViewResource(R.id.network_icon, iconRes);
        notificationView.setTextViewText(R.id.download_speed, "↓ " + formatSpeed(downloadSpeed) + "/s");
        notificationView.setTextViewText(R.id.upload_speed, "↑ " + formatSpeed(uploadSpeed) + "/s");
        notificationView.setTextViewText(R.id.wifi_total, "WiFi: " + formatBytes(totalWifiBytes));
        notificationView.setTextViewText(R.id.data_total, "Data: " + formatBytes(totalDataBytes));
        notificationBuilder.setSmallIcon(iconRes);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());

        Intent uiUpdateIntent = new Intent("USAGE_UPDATE");
        uiUpdateIntent.putExtra("wifi_usage", formatBytes(totalWifiBytes));
        uiUpdateIntent.putExtra("data_usage", formatBytes(totalDataBytes));
        uiUpdateIntent.putExtra("download_speed", formatSpeed(downloadSpeed));
        uiUpdateIntent.putExtra("upload_speed", formatSpeed(uploadSpeed));
        uiUpdateIntent.putExtra("network_status", connectionType);
        sendBroadcast(uiUpdateIntent);
    }

    private String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 0) bytesPerSec = 0;
        return formatBytesInternal(bytesPerSec);
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) bytes = 0;
        return formatBytesInternal(bytes);
    }

    private String formatBytesInternal(long bytes) {
        if (bytes < 1024) return String.format(Locale.getDefault(), "%d B", bytes);
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "NetX Service", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Real-time network speed and usage monitor");
                channel.setShowBadge(false);
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification Channel Created");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        removeOverlay();
        stopForeground(true);
        super.onDestroy();
    }
}