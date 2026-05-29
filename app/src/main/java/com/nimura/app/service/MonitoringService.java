package com.nimura.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.lifecycle.LifecycleService;

import com.google.mediapipe.framework.image.MPImage;
import com.nimura.app.MainActivity;
import com.nimura.app.R;
import com.nimura.app.data.NimuraDatabase;
import com.nimura.app.data.entity.Measurement;
import com.nimura.app.data.entity.Session;
import com.nimura.app.detection.CameraManager;
import com.nimura.app.detection.DevicePostureHelper;
import com.nimura.app.detection.FaceLandmarkerHelper;
import com.nimura.app.nudge.NudgeEngine;
import com.nimura.app.scoring.BlinkTracker;
import com.nimura.app.scoring.FatigueLevel;
import com.nimura.app.scoring.FatigueScoreEngine;
import com.nimura.app.scoring.SamplingController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitoringService extends LifecycleService implements
        FaceLandmarkerHelper.ResultListener,
        SamplingController.EvaluationListener,
        BatteryMonitor.BatteryListener {

    private static final String TAG = "MonitoringService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "monitoring_channel";

    public static final String ACTION_STOP = "com.nimura.app.STOP_MONITORING";

    private CameraManager cameraManager;
    private FaceLandmarkerHelper faceLandmarkerHelper;
    private DevicePostureHelper devicePostureHelper;
    private SamplingController samplingController;
    private BlinkTracker blinkTracker;
    private FatigueScoreEngine fatigueScoreEngine;
    private BatteryMonitor batteryMonitor;
    private NudgeEngine nudgeEngine;
    private NimuraDatabase database;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private long currentSessionId = -1;
    private volatile float latestHeadPitch = 0;
    private volatile float latestHeadRoll = 0;
    private volatile float latestDeviceTilt = 90;
    private volatile float latestEarAvg = 0;
    private long lastFaceTimestamp = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        createNotificationChannel();

        database = NimuraDatabase.getInstance(this);
        blinkTracker = new BlinkTracker();
        fatigueScoreEngine = new FatigueScoreEngine();
        samplingController = new SamplingController();
        samplingController.setListener(this);

        batteryMonitor = new BatteryMonitor(this);
        batteryMonitor.setListener(this);

        cameraManager = new CameraManager(this);
        cameraManager.setFrameListener(this::onCameraFrame);

        nudgeEngine = new NudgeEngine(this);

        faceLandmarkerHelper = new FaceLandmarkerHelper(this, this);

        devicePostureHelper = new DevicePostureHelper(this);
        devicePostureHelper.setListener((tilt, roll) -> {
            latestDeviceTilt = tilt;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (ACTION_STOP.equals(intent != null ? intent.getAction() : null)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNotification();
        startMonitoring();

        return START_STICKY;
    }

    private void startForegroundNotification() {
        Notification notification = buildNotification("計測を開始しました");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startMonitoring() {
        dbExecutor.execute(() -> {
            Session session = new Session();
            session.startTime = System.currentTimeMillis();
            session.isActive = true;
            currentSessionId = database.sessionDao().insertSession(session);
            Log.d(TAG, "Session created: " + currentSessionId);
        });

        // Start everything — camera runs continuously
        cameraManager.startCamera(this);
        cameraManager.setAnalysisEnabled(true);
        devicePostureHelper.startListening();
        batteryMonitor.startMonitoring();
        blinkTracker.startWindow();
        samplingController.start();
    }

    // Camera frames are processed continuously (no more intermittent sampling)
    private void onCameraFrame(MPImage image, long timestampMs) {
        faceLandmarkerHelper.setImageDimensions(image.getWidth(), image.getHeight());

        if (timestampMs <= lastFaceTimestamp) {
            timestampMs = lastFaceTimestamp + 1;
        }
        lastFaceTimestamp = timestampMs;

        try {
            faceLandmarkerHelper.detectAsync(image, timestampMs);
        } catch (Exception e) {
            Log.e(TAG, "Face detect error: " + e.getMessage());
        }
    }

    // FaceLandmarkerHelper.ResultListener
    @Override
    public void onFaceResult(float earLeft, float earRight, float earAvg, boolean isBlinkDetected,
                             float headPitch, float headRoll) {
        latestEarAvg = earAvg;
        latestHeadPitch = headPitch;
        latestHeadRoll = headRoll;
        blinkTracker.addFrame(earAvg);
    }

    @Override
    public void onFaceError(String error) {
        Log.e(TAG, "Face detection error: " + error);
    }

    // SamplingController.EvaluationListener — called every 60 seconds
    @Override
    public void onBaselineComplete() {
        Log.d(TAG, String.format("Baseline complete: %.1f blinks/min",
                blinkTracker.getBaselineBlinkRate()));
        updateNotification("ベースライン取得完了 — モニタリング中");
    }

    @Override
    public void onEvaluationDue() {
        if (!blinkTracker.hasValidData()) {
            Log.d(TAG, "Not enough data for evaluation, skipping");
            return;
        }

        float blinkRate = blinkTracker.getBlinkRate();
        float perclos = blinkTracker.getPERCLOS();
        float baselineDrop = blinkTracker.getBaselineDropPercentage();

        // Posture
        float deviceComponent = 90.0f - latestDeviceTilt;
        float headComponent = Math.max(0, -latestHeadPitch) * 0.3f;
        float neckForwardAngle = deviceComponent + headComponent;
        float headRollAbs = Math.abs(latestHeadRoll);

        Log.d(TAG, String.format("Eval: blink=%.1f/min perclos=%.3f baselineDrop=%.0f%% neck=%.1f roll=%.1f",
                blinkRate, perclos, baselineDrop * 100, neckForwardAngle, headRollAbs));

        Measurement measurement = fatigueScoreEngine.computeMeasurement(
                currentSessionId, blinkRate, latestEarAvg, perclos,
                neckForwardAngle, headRollAbs);

        FatigueLevel level = FatigueLevel.fromScore(measurement.compositeFatigueScore);
        Log.d(TAG, "Fatigue score: " + measurement.compositeFatigueScore + " (" + level + ")");

        // Save to DB; only trigger nudge after baseline is established
        boolean canNudge = samplingController.isMonitoring();
        dbExecutor.execute(() -> {
            long measurementId = database.measurementDao().insertMeasurement(measurement);
            if (canNudge) {
                nudgeEngine.onFatigueScoreUpdate(measurement.compositeFatigueScore, measurementId);
            } else {
                Log.d(TAG, "Baseline phase, skipping nudge");
            }
        });

        updateNotification(measurement.compositeFatigueScore, level);

        // Reset window for next evaluation period
        blinkTracker.startWindow();
    }

    // BatteryMonitor.BatteryListener
    @Override
    public void onBatteryLow() {
        Log.w(TAG, "Battery low, stopping monitoring");
        stopSelf();
    }

    private void updateNotification(int score, FatigueLevel level) {
        String levelText;
        switch (level) {
            case GOOD: levelText = "良好"; break;
            case MILD: levelText = "軽度疲労"; break;
            case MODERATE: levelText = "中度疲労"; break;
            case SEVERE: levelText = "重度疲労"; break;
            default: levelText = "";
        }
        updateNotification("疲労スコア: " + score + " (" + levelText + ")");
    }

    private void updateNotification(String text) {
        Notification notification = buildNotification(text);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String contentText) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, MonitoringService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nimura - 計測中")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(contentPendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "計測モニタリング",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("バックグラウンド計測中の通知");
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroying");

        samplingController.stop();
        batteryMonitor.stopMonitoring();
        devicePostureHelper.stopListening();
        cameraManager.close();
        faceLandmarkerHelper.close();

        if (currentSessionId > 0) {
            dbExecutor.execute(() -> {
                Session session = database.sessionDao().getSessionById(currentSessionId);
                if (session != null) {
                    session.endTime = System.currentTimeMillis();
                    session.isActive = false;
                    database.sessionDao().updateSession(session);
                }
            });
        }

        fatigueScoreEngine.reset();
        nudgeEngine.shutdown();
        dbExecutor.shutdown();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return null;
    }
}
