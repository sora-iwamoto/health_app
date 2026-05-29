package com.nimura.app.nudge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.nimura.app.scoring.FatigueLevel;

public class NudgeNotificationManager {

    private static final String CHANNEL_ID = "nudge_channel";
    private static final int NUDGE_NOTIFICATION_ID = 2;

    public static final String EXTRA_NUDGE_LOG_ID = "nudge_log_id";
    public static final String EXTRA_MEASUREMENT_ID = "measurement_id";

    private final Context context;

    public NudgeNotificationManager(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "健康リマインド",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("疲労検出時のリマインド通知");
        channel.enableVibration(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    public void showNudgeNotification(FatigueLevel level, int score, long nudgeLogId, long measurementId) {
        String title = NudgeContent.getTitle(level);
        String message = NudgeContent.getMessage(level);

        // "実行する" action
        Intent acceptIntent = new Intent(context, NudgeResponseReceiver.class);
        acceptIntent.setAction(NudgeResponseReceiver.ACTION_ACCEPTED);
        acceptIntent.putExtra(EXTRA_NUDGE_LOG_ID, nudgeLogId);
        PendingIntent acceptPending = PendingIntent.getBroadcast(
                context, 100, acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "あとで" action
        Intent dismissIntent = new Intent(context, NudgeResponseReceiver.class);
        dismissIntent.setAction(NudgeResponseReceiver.ACTION_DISMISSED);
        dismissIntent.putExtra(EXTRA_NUDGE_LOG_ID, nudgeLogId);
        PendingIntent dismissPending = PendingIntent.getBroadcast(
                context, 101, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_media_play, "実行する", acceptPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "あとで", dismissPending);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NUDGE_NOTIFICATION_ID, builder.build());
        }
    }
}
