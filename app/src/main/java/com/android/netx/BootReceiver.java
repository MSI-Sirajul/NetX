package com.android.netx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_REBOOT.equals(intent.getAction()) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            
            Log.d("NetX", "Device booted, starting service");
            
            // Start service on boot
            Intent serviceIntent = new Intent(context, NetworkMonitorService.class);
            context.startService(serviceIntent);
        }
		
    }
    
}