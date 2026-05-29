package com.nimura.app.nudge;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.nimura.app.data.NimuraDatabase;
import com.nimura.app.data.entity.NudgeLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NudgeResponseReceiver extends BroadcastReceiver {

    private static final String TAG = "NudgeResponseReceiver";
    public static final String ACTION_ACCEPTED = "com.nimura.app.NUDGE_ACCEPTED";
    public static final String ACTION_DISMISSED = "com.nimura.app.NUDGE_DISMISSED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        long nudgeLogId = intent.getLongExtra(NudgeNotificationManager.EXTRA_NUDGE_LOG_ID, -1);

        if (nudgeLogId < 0) return;

        String response;
        if (ACTION_ACCEPTED.equals(action)) {
            response = "accepted";
        } else if (ACTION_DISMISSED.equals(action)) {
            response = "dismissed";
        } else {
            return;
        }

        Log.d(TAG, "Nudge response: " + response + " for nudgeLogId: " + nudgeLogId);

        // Dismiss the notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(2);
        }

        // Update DB
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            NimuraDatabase db = NimuraDatabase.getInstance(context);
            NudgeLog nudgeLog = db.nudgeLogDao().getNudgeLogById(nudgeLogId);
            if (nudgeLog != null) {
                nudgeLog.userResponse = response;
                nudgeLog.responseTimeMs = System.currentTimeMillis() - nudgeLog.timestamp;
                db.nudgeLogDao().updateNudgeLog(nudgeLog);
            }
            executor.shutdown();
        });
    }
}
