package com.android.netx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            // চেক করুন ইউজার আগে ওভারলে ডিজেবল করে রেখেছিল কিনা
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean isOverlayDisabled = prefs.getBoolean("disable_overlay", false);

            if (!isOverlayDisabled) {
                Intent serviceIntent = new Intent(context, NetworkMonitorService.class);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}