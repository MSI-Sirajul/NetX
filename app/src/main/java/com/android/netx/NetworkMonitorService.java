package com.android.netx;

import android.annotation.SuppressLint;
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
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

    private static final String CHANNEL_ID = "netx_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Android 14 Flag (Hardcoded to avoid symbol error)
    // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    // আমরা মেনিফেস্টে ডিক্লেয়ার করেছি, তাই কোডে টাইপ না দিলেও চলবে (যদি SDK কম হয়)

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams overlayParams;

    private NotificationManager notificationManager;
    private Notification.Builder notificationBuilder;
    private RemoteViews notificationLayout;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private long lastRx = 0;
    private long lastTx = 0;
    private long totalWifi = 0;
    private long totalData = 0;

    private SharedPreferences prefs;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean isConnected = false;
    private boolean isWifi = false;

    // Preferences Variables
    private boolean isOverlayLocked = false;
    private boolean isAutoHide = false;
    private boolean isOverlayDisabled = false;

    // Overlay Views
    private TextView tvDownload, tvUpload;
    private ImageView imgIcon;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        // Load totals
        totalWifi = prefs.getLong("total_wifi", 0);
        totalData = prefs.getLong("total_data", 0);
        
        lastRx = TrafficStats.getTotalRxBytes();
        lastTx = TrafficStats.getTotalTxBytes();

        createOverlay(); 
        
        startForegroundServiceCompat();
        setupNetworkCallback();

        // Start Loop
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateNetworkStats();
                handler.postDelayed(this, 1000); 
            }
        };
        handler.post(updateRunnable);
    }

    private void setupNetworkCallback() {
        if (connectivityManager == null) return;
        
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // ব্যাকগ্রাউন্ড থ্রেড থেকে মেইন থ্রেডে পাঠানো হচ্ছে
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        isConnected = true;
                        updateConnectionType(network);
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                // ব্যাকগ্রাউন্ড থ্রেড থেকে মেইন থ্রেডে পাঠানো হচ্ছে
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        isConnected = false;
                        updateOverlayVisibility();
                    }
                });
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                // ব্যাকগ্রাউন্ড থ্রেড থেকে মেইন থ্রেডে পাঠানো হচ্ছে
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateConnectionType(network);
                    }
                });
            }
        };
        
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void updateConnectionType(Network network) {
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        if (caps != null) {
            boolean newIsWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            if (isWifi != newIsWifi) {
                isWifi = newIsWifi;
            }
        }
        updateOverlayVisibility();
    }

    private void startForegroundServiceCompat() {
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) { // Android 12+
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        notificationLayout = new RemoteViews(getPackageName(), R.layout.notification_layout);
        
        notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.lte_strong) 
                .setCustomContentView(notificationLayout)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        // Android 14 তে টাইপ লাগে, কিন্তু মেনিফেস্টে থাকায় এখানে শুধু নোটিফিকেশন দিলেও কাজ করবে
        // কম্পাইল এরর এড়াতে আমরা সাধারণ startForeground ব্যবহার করছি
        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createOverlay() {
        if (overlayView != null) return;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.overlay_layout, null);
        
        tvDownload = overlayView.findViewById(R.id.download_speed);
        tvUpload = overlayView.findViewById(R.id.upload_speed);
        imgIcon = overlayView.findViewById(R.id.overlay_icon);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = prefs.getInt("overlay_x", 0); 
        overlayParams.y = prefs.getInt("overlay_y", 0);

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isOverlayLocked) return true;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // নতুন পজিশন ক্যালকুলেট করা
                        int newX = initialX + (int) (event.getRawX() - initialTouchX);
                        int newY = initialY + (int) (event.getRawY() - initialTouchY);

                        // ৩. বাউন্ডারি চেক (যাতে স্ক্রিনের বাইরে না যায়)
                        // বাম পাশে 0 এর নিচে যাবে না এবং ডান পাশে (Screen Width - View Width) এর বেশি যাবে না
                        if (newX < 0) newX = 0;
                        if (newX > screenWidth - v.getWidth()) newX = screenWidth - v.getWidth();

                        // উপরে 0 এর নিচে যাবে না এবং নিচে (Screen Height - View Height) এর বেশি যাবে না
                        if (newY < 0) newY = 0;
                        if (newY > screenHeight - v.getHeight()) newY = screenHeight - v.getHeight();

                        overlayParams.x = newX;
                        overlayParams.y = newY;
                        
                        windowManager.updateViewLayout(overlayView, overlayParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("overlay_x", overlayParams.x)
                                .putInt("overlay_y", overlayParams.y).apply();
                        return true;
                }
                return false;
            }
        });

        updateOverlayVisibility();
    }

    private void updateOverlayVisibility() {
        // ১. সেটিংস লোড করা
        isOverlayLocked = prefs.getBoolean("lock_overlay", false);
        isAutoHide = prefs.getBoolean("auto_hide", false);
        isOverlayDisabled = prefs.getBoolean("disable_overlay", false);

        // ২. সেফটি চেক: overlayView যদি এখনো তৈরি না হয়ে থাকে, তবে তৈরি করার চেষ্টা করুন
        if (overlayView == null) {
            createOverlay();
            // তৈরির পরেও যদি null থাকে (কোনো কারণে ফেইল হলে), তাহলে মেথড থেকে বের হয়ে যান
            if (overlayView == null) return; 
        }

        boolean shouldShow = !isOverlayDisabled;
        if (isAutoHide && !isConnected) {
            shouldShow = false;
        }

        if (shouldShow) {
            // ফিক্স: আগে চেক করুন প্যারেন্ট null কিনা, তারপর অ্যাড করুন
            if (overlayView.getParent() == null) {
                try {
                    windowManager.addView(overlayView, overlayParams);
                } catch (Exception e) {
                    Log.e("NetX", "Error adding overlay", e);
                }
            }
        } else {
            // ফিক্স: রিমুভ করার আগেও চেক করে নেওয়া ভালো
            if (overlayView.getParent() != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception e) {
                    Log.e("NetX", "Error removing overlay", e);
                }
            }
        }
    }

    private void updateNetworkStats() {
        updateOverlayVisibility();

        long currentRx = TrafficStats.getTotalRxBytes();
        long currentTx = TrafficStats.getTotalTxBytes();

        if (currentRx < lastRx) lastRx = currentRx;
        if (currentTx < lastTx) lastTx = currentTx;

        long rxSpeed = currentRx - lastRx;
        long txSpeed = currentTx - lastTx;

        lastRx = currentRx;
        lastTx = currentTx;

        if (isConnected) {
            if (isWifi) {
                totalWifi += (rxSpeed + txSpeed);
            } else {
                totalData += (rxSpeed + txSpeed);
            }
            prefs.edit().putLong("total_wifi", totalWifi)
                    .putLong("total_data", totalData).apply();
        }

        String dlStr = formatSpeed(rxSpeed);
        String ulStr = formatSpeed(txSpeed);
        String wifiStr = formatBytes(totalWifi);
        String dataStr = formatBytes(totalData);
        
        int iconRes = !isConnected ? R.drawable.no_network : (isWifi ? R.drawable.wifi_strong : R.drawable.lte_strong);

        // Update Notification
        if (notificationLayout != null) {
            notificationLayout.setTextViewText(R.id.download_speed, "↓ " + dlStr);
            notificationLayout.setTextViewText(R.id.upload_speed, "↑ " + ulStr);
            notificationLayout.setTextViewText(R.id.wifi_total, "WiFi: " + wifiStr);
            notificationLayout.setTextViewText(R.id.data_total, "Data: " + dataStr);
            notificationLayout.setImageViewResource(R.id.network_icon, iconRes);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        // Update Overlay
        if (overlayView != null && overlayView.getParent() != null) {
            if (tvDownload != null) tvDownload.setText(dlStr);
            if (tvUpload != null) tvUpload.setText(ulStr);
            if (imgIcon != null) imgIcon.setImageResource(iconRes);
        }

        // Send Broadcast
        Intent intent = new Intent("NETX_UPDATE");
        intent.putExtra("dl", dlStr);
        intent.putExtra("ul", ulStr);
        intent.putExtra("wifi", wifiStr);
        intent.putExtra("data", dataStr);
        intent.putExtra("status", isConnected ? "Active" : "Disconnected");
        intent.putExtra("isWifi", isWifi);
        sendBroadcast(intent);
    }

    private String formatSpeed(long bytes) {
        return formatBytes(bytes) + "/s";
    }

    private String formatBytes(long bytes) {
        double b = (double) bytes;
        
        // ১. ৯৯ বাইট বা তার নিচে হলে সরাসরি বাইট দেখাবে (যেমন: 50 B)
        if (b <= 99) {
            return String.format(Locale.getDefault(), "%d B", bytes);
        } 
        // ২. ৯৯ বাইট পার হলেই KB তে দেখাবে (যেমন: 100 B -> 0.1 KB)
        // ৯৯ কেবি (approx 101376 bytes) পর্যন্ত KB থাকবে
        else if (b < 101376) { 
            return String.format(Locale.getDefault(), "%.1f KB", b / 1024.0);
        } 
        // ৩. ৯৯ কেবি পার হলেই MB তে দেখাবে (যেমন: 100 KB -> 0.1 MB)
        else if (b < 101376 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", b / (1024.0 * 1024.0));
        } 
        // ৪. বাকি সব GB
        else {
            return String.format(Locale.getDefault(), "%.2f GB", b / (1024.0 * 1024.0 * 1024.0));
        }
    }

    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "NetX Monitor", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        if (overlayView != null && overlayView.getParent() != null) {
            windowManager.removeView(overlayView);
        }
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}