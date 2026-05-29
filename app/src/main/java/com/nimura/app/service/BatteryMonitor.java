package com.nimura.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

public class BatteryMonitor {

    private static final String TAG = "BatteryMonitor";
    private static final int LOW_BATTERY_THRESHOLD = 15;

    private final Context context;
    private BroadcastReceiver batteryReceiver;
    private BatteryListener listener;

    public interface BatteryListener {
        void onBatteryLow();
    }

    public BatteryMonitor(Context context) {
        this.context = context;
    }

    public void setListener(BatteryListener listener) {
        this.listener = listener;
    }

    public void startMonitoring() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    int batteryPct = (level * 100) / scale;
                    if (batteryPct <= LOW_BATTERY_THRESHOLD) {
                        Log.w(TAG, "Battery low: " + batteryPct + "%");
                        if (listener != null) {
                            listener.onBatteryLow();
                        }
                    }
                }
            }
        };
        context.registerReceiver(batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public void stopMonitoring() {
        if (batteryReceiver != null) {
            try {
                context.unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // Already unregistered
            }
            batteryReceiver = null;
        }
    }
}
