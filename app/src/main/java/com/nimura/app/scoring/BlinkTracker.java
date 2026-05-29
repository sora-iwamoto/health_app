package com.nimura.app.scoring;

import android.util.Log;

/**
 * Tracks blink events using EAR (Eye Aspect Ratio) with techniques from
 * reference implementation (blink_movie_MediaPipe_re_FeedbackChange_JavaVer):
 *
 * - Pixel-scale EAR calculation (corrects for image aspect ratio)
 * - Consecutive frame requirement (2 frames minimum to confirm blink)
 * - Personalized threshold via calibration (open/closed eye midpoint)
 * - Baseline blink rate tracking (2-min baseline, then deviation %)
 */
public class BlinkTracker {

    private static final String TAG = "BlinkTracker";

    // Default threshold if not calibrated (reference project uses 0.10 with pixel-scale)
    private static final float DEFAULT_EAR_THRESHOLD = 0.2f;

    // Must stay below threshold for this many consecutive frames to count as blink
    // (prevents noise-induced false positives)
    private static final int EAR_CONSEC_FRAMES = 2;

    // Calibration: interpolation factor between closed and open EAR
    // 0.5 = midpoint (reference project default)
    private static final float CALIBRATION_ALPHA = 0.5f;

    private static final int MIN_FRAMES_FOR_VALID_RESULT = 10;

    // Baseline tracking
    private static final long BASELINE_DURATION_MS = 2 * 60 * 1000; // 2 minutes
    private static final int BASELINE_WINDOW_MS = 60_000; // 1-minute rolling window

    private enum EyeState { OPEN, CLOSED }

    private EyeState currentState = EyeState.OPEN;
    private int earBelowThresholdCount = 0; // consecutive frames below threshold
    private int blinkCount = 0;
    private int totalFrames = 0;
    private int closedFrames = 0;
    private long windowStartTimeMs = 0;

    // Personalized threshold (set via calibration)
    private float earThreshold = DEFAULT_EAR_THRESHOLD;
    private boolean isCalibrated = false;

    // Baseline blink rate
    private float baselineBlinkRate = -1; // -1 = not yet established
    private long monitoringStartTimeMs = 0;
    private int totalBlinksFromStart = 0;

    public BlinkTracker() {
        monitoringStartTimeMs = System.currentTimeMillis();
    }

    public void startWindow() {
        blinkCount = 0;
        totalFrames = 0;
        closedFrames = 0;
        earBelowThresholdCount = 0;
        currentState = EyeState.OPEN;
        windowStartTimeMs = System.currentTimeMillis();
    }

    /**
     * Process a frame with EAR value.
     * Uses consecutive frame logic from reference project to reduce false positives.
     */
    public void addFrame(float earAvg) {
        if (Float.isNaN(earAvg)) {
            // Face not properly detected — reset counter (reference project pattern)
            earBelowThresholdCount = 0;
            return;
        }

        totalFrames++;

        boolean eyesClosed = earAvg < earThreshold;

        if (eyesClosed) {
            closedFrames++;
            earBelowThresholdCount++;
        } else {
            // Transition from closed to open: check if it was a real blink
            if (earBelowThresholdCount >= EAR_CONSEC_FRAMES) {
                blinkCount++;
                totalBlinksFromStart++;
                updateBaseline();
            }
            earBelowThresholdCount = 0;
        }
    }

    /**
     * Called when face is not detected. Resets blink detection state.
     */
    public void onFaceLost() {
        earBelowThresholdCount = 0;
        currentState = EyeState.OPEN;
    }

    /**
     * Calibrate the EAR threshold for this user.
     * Uses the midpoint between open and closed EAR values.
     *
     * @param openEarMedian  median EAR when eyes are open
     * @param closedEarMedian median EAR when eyes are closed
     */
    public void calibrate(float openEarMedian, float closedEarMedian) {
        if (Float.isNaN(openEarMedian) || Float.isNaN(closedEarMedian)) {
            Log.w(TAG, "Calibration failed: NaN values");
            return;
        }
        earThreshold = closedEarMedian + CALIBRATION_ALPHA * (openEarMedian - closedEarMedian);
        isCalibrated = true;
        Log.d(TAG, String.format("Calibrated: open=%.3f, closed=%.3f, threshold=%.3f",
                openEarMedian, closedEarMedian, earThreshold));
    }

    private void updateBaseline() {
        long elapsed = System.currentTimeMillis() - monitoringStartTimeMs;
        if (elapsed >= BASELINE_DURATION_MS && baselineBlinkRate < 0) {
            // Establish baseline after 2 minutes
            float elapsedMinutes = elapsed / 60000.0f;
            baselineBlinkRate = totalBlinksFromStart / elapsedMinutes;
            Log.d(TAG, String.format("Baseline established: %.1f blinks/min", baselineBlinkRate));
        }
    }

    /**
     * Get blink rate extrapolated to blinks per minute.
     */
    public float getBlinkRate() {
        long elapsedMs = System.currentTimeMillis() - windowStartTimeMs;
        if (elapsedMs <= 0) return 0;
        float elapsedMinutes = elapsedMs / 60000.0f;
        if (elapsedMinutes <= 0) return 0;
        return blinkCount / elapsedMinutes;
    }

    /**
     * Get PERCLOS: fraction of frames where eyes were closed (EAR < threshold).
     */
    public float getPERCLOS() {
        if (totalFrames == 0) return 0;
        return (float) closedFrames / totalFrames;
    }

    /**
     * Get the drop percentage from baseline blink rate.
     * 0.0 = no drop (or above baseline), 1.0 = 100% drop (no blinks).
     * Returns -1 if baseline is not yet established.
     */
    public float getBaselineDropPercentage() {
        if (baselineBlinkRate <= 0) return -1;
        float currentRate = getBlinkRate();
        float ratio = currentRate / baselineBlinkRate;
        return Math.max(0, 1.0f - ratio);
    }

    public boolean hasBaseline() {
        return baselineBlinkRate > 0;
    }

    public float getBaselineBlinkRate() {
        return baselineBlinkRate;
    }

    public int getBlinkCount() {
        return blinkCount;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public boolean hasValidData() {
        return totalFrames >= MIN_FRAMES_FOR_VALID_RESULT;
    }

    public float getEarThreshold() {
        return earThreshold;
    }

    public boolean isCalibrated() {
        return isCalibrated;
    }
}
