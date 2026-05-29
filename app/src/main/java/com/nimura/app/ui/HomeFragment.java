package com.nimura.app.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.nimura.app.R;
import com.nimura.app.data.NimuraDatabase;
import com.nimura.app.data.entity.Measurement;
import com.nimura.app.data.entity.Session;
import com.nimura.app.scoring.FatigueLevel;
import com.nimura.app.service.MonitoringService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 10_000; // 10 seconds

    private TextView textFatigueScore;
    private TextView textFatigueLevel;
    private TextView textBlinkRate;
    private TextView textPostureScore;
    private MaterialButton buttonStartStop;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isServiceRunning()) {
                loadLatestData();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean cameraGranted = result.get(Manifest.permission.CAMERA);
                if (cameraGranted != null && cameraGranted) {
                    startMonitoring();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textFatigueScore = view.findViewById(R.id.textFatigueScore);
        textFatigueLevel = view.findViewById(R.id.textFatigueLevel);
        textBlinkRate = view.findViewById(R.id.textBlinkRate);
        textPostureScore = view.findViewById(R.id.textPostureScore);
        buttonStartStop = view.findViewById(R.id.buttonStartStop);

        buttonStartStop.setOnClickListener(v -> {
            if (isServiceRunning()) {
                stopMonitoring();
            } else {
                requestPermissionsAndStart();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    private void startAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        if (isServiceRunning()) {
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        }
    }

    private void stopAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void updateUI() {
        updateButtonState();
        loadLatestData();
    }

    private void updateButtonState() {
        if (isServiceRunning()) {
            buttonStartStop.setText("計測を止める");
            buttonStartStop.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_severe));
        } else {
            buttonStartStop.setText("計測を始める");
            buttonStartStop.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary));
        }
    }

    private void loadLatestData() {
        executor.execute(() -> {
            NimuraDatabase db = NimuraDatabase.getInstance(requireContext());
            Session latestSession = db.sessionDao().getLatestSession();
            if (latestSession == null) return;

            List<Measurement> measurements = db.measurementDao().getRecentMeasurements(latestSession.id, 1);
            if (measurements.isEmpty()) return;

            Measurement latest = measurements.get(0);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    textFatigueScore.setText(String.valueOf(latest.compositeFatigueScore));
                    FatigueLevel level = FatigueLevel.fromScore(latest.compositeFatigueScore);
                    int color;
                    String levelText;
                    switch (level) {
                        case GOOD: color = R.color.green_good; levelText = "良好"; break;
                        case MILD: color = R.color.yellow_mild; levelText = "軽度疲労"; break;
                        case MODERATE: color = R.color.orange_moderate; levelText = "中度疲労"; break;
                        case SEVERE: color = R.color.red_severe; levelText = "重度疲労"; break;
                        default: color = R.color.green_good; levelText = "";
                    }
                    textFatigueScore.setTextColor(ContextCompat.getColor(requireContext(), color));
                    textFatigueLevel.setText(levelText);
                    textBlinkRate.setText(String.format("%.0f回/分", latest.blinkRate));
                    textPostureScore.setText(String.valueOf(latest.postureFatigueScore) + "点");
                });
            }
        });
    }

    private void requestPermissionsAndStart() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS};
        } else {
            permissions = new String[]{Manifest.permission.CAMERA};
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            startMonitoring();
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    private void startMonitoring() {
        Intent intent = new Intent(requireContext(), MonitoringService.class);
        ContextCompat.startForegroundService(requireContext(), intent);
        updateButtonState();
        startAutoRefresh();
    }

    private void stopMonitoring() {
        Intent intent = new Intent(requireContext(), MonitoringService.class);
        intent.setAction(MonitoringService.ACTION_STOP);
        requireContext().startService(intent);
        updateButtonState();

        // Refresh data after a short delay
        buttonStartStop.postDelayed(this::updateUI, 500);
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MonitoringService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
