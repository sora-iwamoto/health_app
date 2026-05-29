package com.nimura.app.nudge;

import android.content.Context;
import android.util.Log;

import com.nimura.app.data.NimuraDatabase;
import com.nimura.app.data.entity.NudgeLog;
import com.nimura.app.scoring.FatigueLevel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NudgeEngine {

    private static final String TAG = "NudgeEngine";
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes

    private final Context context;
    private final NudgeNotificationManager notificationManager;
    private final NimuraDatabase database;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private long lastNudgeTimestamp = 0;
    private boolean isControlGroup = false;

    public NudgeEngine(Context context) {
        this.context = context;
        this.notificationManager = new NudgeNotificationManager(context);
        this.database = NimuraDatabase.getInstance(context);
    }

    public void setControlGroup(boolean controlGroup) {
        this.isControlGroup = controlGroup;
    }

    public void onFatigueScoreUpdate(int score, long measurementId) {
        FatigueLevel level = FatigueLevel.fromScore(score);

        if (level == FatigueLevel.GOOD) {
            return; // No nudge needed
        }

        // Check cooldown
        long now = System.currentTimeMillis();
        if (now - lastNudgeTimestamp < COOLDOWN_MS) {
            Log.d(TAG, "Nudge cooldown active, skipping");
            return;
        }

        lastNudgeTimestamp = now;

        String title = NudgeContent.getTitle(level);
        String message = NudgeContent.getMessage(level);

        // Create nudge log
        NudgeLog nudgeLog = new NudgeLog();
        nudgeLog.measurementId = measurementId;
        nudgeLog.timestamp = now;
        nudgeLog.nudgeLevel = level.name().toLowerCase();
        nudgeLog.nudgeContent = title + ": " + message;
        nudgeLog.userResponse = isControlGroup ? "control_group" : "ignored";
        nudgeLog.responseTimeMs = 0;

        dbExecutor.execute(() -> {
            long nudgeLogId = database.nudgeLogDao().insertNudgeLog(nudgeLog);

            if (!isControlGroup) {
                notificationManager.showNudgeNotification(level, score, nudgeLogId, measurementId);
                Log.d(TAG, "Nudge sent: " + level + " (score: " + score + ")");
            } else {
                Log.d(TAG, "Control group: nudge logged but not shown");
            }
        });
    }

    public void shutdown() {
        dbExecutor.shutdown();
    }
}
