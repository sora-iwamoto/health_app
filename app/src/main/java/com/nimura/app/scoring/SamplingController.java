package com.nimura.app.scoring;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Controls continuous monitoring with periodic evaluation.
 *
 * Old design: 3 seconds sampling every 30 seconds (insufficient data)
 * New design: Always sampling, evaluate every 60 seconds
 *
 * Timeline:
 *   0:00 - 2:00  → BASELINE phase (collecting personal baseline blink rate)
 *   2:00+        → MONITORING phase (continuous, evaluate every 60s)
 */
public class SamplingController {

    private static final String TAG = "SamplingController";

    // Evaluate accumulated data every 60 seconds
    private static final long EVALUATION_INTERVAL_MS = 60_000;

    // Baseline collection period: 2 minutes
    private static final long BASELINE_DURATION_MS = 2 * 60 * 1000;

    public enum Phase { IDLE, BASELINE, MONITORING }

    private Phase currentPhase = Phase.IDLE;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EvaluationListener listener;
    private Runnable evaluationRunnable;
    private long startTimeMs = 0;
    private int evaluationCount = 0;

    public interface EvaluationListener {
        void onEvaluationDue();
        void onBaselineComplete();
    }

    public void setListener(EvaluationListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (currentPhase != Phase.IDLE) return;
        startTimeMs = System.currentTimeMillis();
        evaluationCount = 0;
        currentPhase = Phase.BASELINE;
        Log.d(TAG, "Started — BASELINE phase (2 min)");

        scheduleNextEvaluation();
    }

    public void stop() {
        Log.d(TAG, "Stopped");
        currentPhase = Phase.IDLE;
        if (evaluationRunnable != null) {
            handler.removeCallbacks(evaluationRunnable);
            evaluationRunnable = null;
        }
    }

    private void scheduleNextEvaluation() {
        evaluationRunnable = this::onEvaluation;
        handler.postDelayed(evaluationRunnable, EVALUATION_INTERVAL_MS);
    }

    private void onEvaluation() {
        if (currentPhase == Phase.IDLE) return;

        evaluationCount++;
        long elapsed = System.currentTimeMillis() - startTimeMs;

        // Check if baseline phase is complete
        if (currentPhase == Phase.BASELINE && elapsed >= BASELINE_DURATION_MS) {
            currentPhase = Phase.MONITORING;
            Log.d(TAG, "BASELINE phase complete → MONITORING phase");
            if (listener != null) {
                listener.onBaselineComplete();
            }
        }

        Log.d(TAG, String.format("Evaluation #%d (phase=%s, elapsed=%.0fs)",
                evaluationCount, currentPhase, elapsed / 1000.0));

        if (listener != null) {
            listener.onEvaluationDue();
        }

        scheduleNextEvaluation();
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    public boolean isMonitoring() {
        return currentPhase == Phase.MONITORING;
    }

    public int getEvaluationCount() {
        return evaluationCount;
    }
}
