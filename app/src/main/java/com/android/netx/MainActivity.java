package com.android.netx;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private static final int PERMISSION_REQ_CODE = 101;
    private static final String PERM_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    // Layout Containers
    private View permissionLayout, dashboardLayout;

    // Permission UI Elements
    private ImageView animIcon;
    private TextView animTitle, animDesc;
    private LinearLayout rowOverlay, rowNotif;
    private Switch permOverlayToggle, permNotifToggle;
    private Button btnDone;

    // Dashboard UI Elements
    private TextView currentStatus, wifiUsage, dataUsage, currentDownload, currentUpload;
    private Switch lockOverlayToggle, autoHideToggle, disableOverlayToggle;
    private ImageView profileBt, githubBt;

    private boolean isAnimationPlayed = false;

    // Broadcast Receiver
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            // UI Updates...
            if (currentDownload != null) currentDownload.setText(intent.getStringExtra("dl"));
            if (currentUpload != null) currentUpload.setText(intent.getStringExtra("ul"));
            if (wifiUsage != null) wifiUsage.setText(intent.getStringExtra("wifi"));
            if (dataUsage != null) dataUsage.setText(intent.getStringExtra("data"));
            
            String status = intent.getStringExtra("status");
            if (currentStatus != null && status != null) {
                currentStatus.setText(status);
                if ("Active".equals(status)) {
                    currentStatus.setTextColor(getColor(R.color.app_primary));
                    currentStatus.setBackgroundResource(R.drawable.bg_status_pill);
                } else {
                    currentStatus.setTextColor(getColor(R.color.app_error));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        initViews();
        
        // Check if permissions are already granted
        if (areAllPermissionsGranted()) {
            showDashboard();
            startMonitorService();
        } else {
            showPermissionScreen();
        }
    }

    private void initViews() {
        // Containers
        permissionLayout = findViewById(R.id.permission_layout);
        dashboardLayout = findViewById(R.id.dashboard_layout);

        // Permission UI
        animIcon = findViewById(R.id.animIcon);
        animTitle = findViewById(R.id.animTitle);
        animDesc = findViewById(R.id.animDesc);
        rowOverlay = findViewById(R.id.rowOverlay);
        rowNotif = findViewById(R.id.rowNotif);
        permOverlayToggle = findViewById(R.id.permOverlayToggle);
        permNotifToggle = findViewById(R.id.permNotifToggle);
        btnDone = findViewById(R.id.btnDone);

        // Dashboard UI
        currentStatus = findViewById(R.id.currentStatus);
        wifiUsage = findViewById(R.id.wifiUsage);
        dataUsage = findViewById(R.id.dataUsage);
        currentDownload = findViewById(R.id.currentDownload);
        currentUpload = findViewById(R.id.currentUpload);
        lockOverlayToggle = findViewById(R.id.lockOverlayToggle);
        autoHideToggle = findViewById(R.id.autoHideToggle);
        disableOverlayToggle = findViewById(R.id.disableOverlayToggle);
        profileBt = findViewById(R.id.profileBt);
        githubBt = findViewById(R.id.githubBt);

        setupDashboardListeners();
        setupPermissionListeners();
    }

    // ==========================================
    // LOGIC: PERMISSION SCREEN & ANIMATIONS
    // ==========================================

    private void showPermissionScreen() {
        permissionLayout.setVisibility(View.VISIBLE);
        dashboardLayout.setVisibility(View.GONE);

        if (!isAnimationPlayed) {
            startIntroAnimations();
            isAnimationPlayed = true;
        }
        updatePermissionToggles();
    }

    private void startIntroAnimations() {
        // Initial States for Animation
        animIcon.setScaleX(0f); animIcon.setScaleY(0f);
        animTitle.setTranslationY(100f); animTitle.setAlpha(0f);
        rowOverlay.setTranslationX(-500f); rowOverlay.setAlpha(0f);
        rowNotif.setTranslationX(-500f); rowNotif.setAlpha(0f);
        btnDone.setAlpha(0f);

        long startTime = 0;

        // 1. Icon Zoom In (Starts at 0ms, Duration 500ms)
        animIcon.animate().scaleX(1f).scaleY(1f).setDuration(500).setStartDelay(startTime).setInterpolator(new DecelerateInterpolator()).start();

        // 2. Title Slide Up (Starts at 300ms)
        animTitle.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(startTime + 300).setInterpolator(new DecelerateInterpolator()).start();

        // 3. Description Typing (Starts at 600ms)
        new Handler().postDelayed(() -> typeText("To provide real-time speed monitoring, NetX requires a few permissions."), startTime + 600);

        // 4. Permissions Slide Right (Starts at 1200ms)
        rowOverlay.animate().translationX(0f).alpha(1f).setDuration(500).setStartDelay(startTime + 1200).setInterpolator(new DecelerateInterpolator()).start();
        rowNotif.animate().translationX(0f).alpha(1f).setDuration(500).setStartDelay(startTime + 1400).setInterpolator(new DecelerateInterpolator()).start();

        // 5. Done Button Fade In (Starts at 1800ms) - Total ~2 seconds
        btnDone.animate().alpha(1f).setDuration(500).setStartDelay(startTime + 1800).start();
    }

    private void typeText(final String text) {
        final Handler handler = new Handler();
        final int delay = 30; // Typing speed
        
        new Thread(() -> {
            for (int i = 0; i <= text.length(); i++) {
                final String sub = text.substring(0, i);
                try { Thread.sleep(delay); } catch (InterruptedException e) {}
                handler.post(() -> animDesc.setText(sub));
            }
        }).start();
    }

    // 1. এই মেথডটি রিপ্লেস করুন
    private void setupPermissionListeners() {
        // Overlay Toggle Logic
        permOverlayToggle.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                permOverlayToggle.setChecked(true);
            }
        });

        // Notification Toggle Logic (UPDATED)
        permNotifToggle.setOnClickListener(v -> {
            // Android 13+ এর জন্য চেক
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(PERM_POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    
                    // টগলটি আপাতত অফ করে রাখব, যতক্ষণ না ইউজার Allow করে
                    permNotifToggle.setChecked(false);

                    // সরাসরি পারমিশন রিকোয়েস্ট (সিস্টেম পপআপ)
                    requestPermissions(new String[]{PERM_POST_NOTIFICATIONS}, PERMISSION_REQ_CODE);
                } else {
                    // ইতিমধ্যে পারমিশন থাকলে অন থাকবে
                    permNotifToggle.setChecked(true);
                }
            } else {
                // Android 13 এর নিচে পারমিশন লাগে না, তাই অটো অন
                permNotifToggle.setChecked(true);
            }
        });

        // Done Button Logic
        btnDone.setOnClickListener(v -> {
            if (areAllPermissionsGranted()) {
                showDashboard();
                startMonitorService();
            } else {
                Toast.makeText(this, "Please allow required permissions", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 2. এই নতুন মেথডটি ক্লাসের ভেতরে যুক্ত করুন (Override Method)
    // এটি সিস্টেম পপআপে Allow/Deny ক্লিক করার পর কল হবে এবং টগল আপডেট করবে
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ইউজার পপআপে Allow করেছে
                permNotifToggle.setChecked(true);
                permNotifToggle.setEnabled(false); // একবার দিলে আর বদলানোর দরকার নেই
            } else {
                // ইউজার Don't Allow করেছে
                permNotifToggle.setChecked(false);
                
                // যদি ইউজার পার্মানেন্টলি Deny করে থাকে, তবে সেটিংস এ পাঠানোর লজিক (অপশনাল)
                if (!shouldShowRequestPermissionRationale(PERM_POST_NOTIFICATIONS)) {
                    Toast.makeText(this, "Notification permission is blocked. Please enable it from Settings.", Toast.LENGTH_LONG).show();
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void updatePermissionToggles() {
        // Check Overlay
        boolean overlayOk = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || Settings.canDrawOverlays(this);
        permOverlayToggle.setChecked(overlayOk);
        permOverlayToggle.setEnabled(!overlayOk); // Disable if already granted

        // Check Notification
        boolean notifOk = (Build.VERSION.SDK_INT < 33) || (checkSelfPermission(PERM_POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        permNotifToggle.setChecked(notifOk);
        permNotifToggle.setEnabled(!notifOk);
    }

    private boolean areAllPermissionsGranted() {
        boolean overlayOk = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || Settings.canDrawOverlays(this);
        boolean notifOk = (Build.VERSION.SDK_INT < 33) || (checkSelfPermission(PERM_POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        return overlayOk && notifOk;
    }

    // ==========================================
    // LOGIC: DASHBOARD
    // ==========================================

    private void showDashboard() {
        permissionLayout.setVisibility(View.GONE);
        dashboardLayout.setVisibility(View.VISIBLE);
        
        // Load settings
        if (lockOverlayToggle != null) lockOverlayToggle.setChecked(prefs.getBoolean("lock_overlay", false));
        if (autoHideToggle != null) autoHideToggle.setChecked(prefs.getBoolean("auto_hide", false));
        if (disableOverlayToggle != null) disableOverlayToggle.setChecked(prefs.getBoolean("disable_overlay", false));
    }

    private void setupDashboardListeners() {
        lockOverlayToggle.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("lock_overlay", c).apply());
        autoHideToggle.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("auto_hide", c).apply());
        disableOverlayToggle.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("disable_overlay", c).apply());
        
        profileBt.setOnClickListener(v -> openUrl("https://md-sirajul-islam.vercel.app"));
        githubBt.setOnClickListener(v -> openUrl("https://github.com/MSI-Sirajul/NetX/"));
    }

    // ==========================================
    // COMMON & SYSTEM
    // ==========================================

    private void startMonitorService() {
        Intent serviceIntent = new Intent(this, NetworkMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 1. পারমিশন স্ক্রিন যদি দেখা যায়, তবে টগলগুলোর অবস্থা আপডেট করুন
        if (findViewById(R.id.permission_layout).getVisibility() == View.VISIBLE) {
            updatePermissionToggles();
        }

        // 2. Android 14 রিসিভার রেজিস্ট্রেশন
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(updateReceiver, new IntentFilter("NETX_UPDATE"), 4);
        } else {
            registerReceiver(updateReceiver, new IntentFilter("NETX_UPDATE"));
        }
        
        // 3. ড্যাশবোর্ড মোডে থাকলে সার্ভিস চেক
        if (areAllPermissionsGranted() && findViewById(R.id.dashboard_layout).getVisibility() == View.VISIBLE) {
             startMonitorService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(updateReceiver); } catch (Exception e) {}
    }
}