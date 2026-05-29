package com.nimura.app.scoring;

import com.nimura.app.data.entity.Measurement;

import java.util.ArrayList;
import java.util.List;

public class FatigueScoreEngine {

    private static final int MOVING_AVERAGE_SIZE = 5;

    // --- Blink rate scoring ---
    // Normal: 15-20 blinks/min (Cleveland Clinic; Cognitive demand & digital screens, 2015)
    // Screen fatigue: drops to 5-7 blinks/min (~66% reduction)
    private static final float BLINK_RATE_NORMAL_LOW = 15.0f;
    private static final float BLINK_RATE_FATIGUE = 7.0f;

    // --- PERCLOS scoring ---
    // Fatigue threshold: 0.15 (15%) — PERCLOS optimization study (ACM, 2022)
    // Drowsiness threshold: 0.3 (30%) — FHWA/NHTSA (Wierwille, 1994)
    private static final float PERCLOS_FATIGUE = 0.15f;
    private static final float PERCLOS_DROWSY = 0.3f;

    // --- Neck forward angle scoring ---
    // 0-15 deg: normal range, minimal cervical load
    // 30 deg: ~40 lbs on cervical spine, onset of strain
    // 45-60 deg: ~50-60 lbs, high risk (Text Neck research, Hansraj 2014)
    private static final float NECK_ANGLE_GOOD = 15.0f;
    private static final float NECK_ANGLE_POOR = 45.0f;

    // Head roll scoring (degrees of side tilt from face landmarks)
    private static final float HEAD_ROLL_GOOD = 5.0f;
    private static final float HEAD_ROLL_POOR = 20.0f;

    // Weights (configurable via settings)
    private float alpha = 0.5f;
    private float beta = 0.5f;

    private final List<Integer> recentScores = new ArrayList<>();

    public void setWeights(float alpha, float beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    /**
     * Compute a Measurement from raw sensor data.
     */
    public Measurement computeMeasurement(long sessionId, float blinkRate, float earAvg,
                                           float perclos, float headTiltAngle,
                                           float shoulderTiltAngle) {
        int eyeScore = computeEyeFatigueScore(blinkRate, perclos);
        int postureScore = computePostureFatigueScore(headTiltAngle, shoulderTiltAngle);
        int compositeScore = computeCompositeScore(eyeScore, postureScore);

        // Add to moving average buffer
        recentScores.add(compositeScore);
        if (recentScores.size() > MOVING_AVERAGE_SIZE) {
            recentScores.remove(0);
        }

        int avgScore = getMovingAverage();

        Measurement m = new Measurement();
        m.sessionId = sessionId;
        m.timestamp = System.currentTimeMillis();
        m.blinkRate = blinkRate;
        m.earValue = earAvg;
        m.perclos = perclos;
        m.headTiltAngle = headTiltAngle;
        m.shoulderTiltAngle = shoulderTiltAngle;
        m.eyeFatigueScore = eyeScore;
        m.postureFatigueScore = postureScore;
        m.compositeFatigueScore = avgScore;

        return m;
    }

    /**
     * Eye fatigue score (0-100).
     * Takes the worse of blink rate score and PERCLOS score.
     */
    int computeEyeFatigueScore(float blinkRate, float perclos) {
        int blinkScore = computeBlinkRateScore(blinkRate);
        int perclosScore = computePERCLOSScore(perclos);
        return Math.max(blinkScore, perclosScore);
    }

    /**
     * Blink rate score (0-100).
     * Score increases as blink rate decreases below normal.
     */
    int computeBlinkRateScore(float blinkRate) {
        if (blinkRate >= BLINK_RATE_NORMAL_LOW) return 0;
        if (blinkRate <= BLINK_RATE_FATIGUE) return 100;
        // Linear interpolation
        float ratio = (BLINK_RATE_NORMAL_LOW - blinkRate) / (BLINK_RATE_NORMAL_LOW - BLINK_RATE_FATIGUE);
        return clamp(Math.round(ratio * 100));
    }

    /**
     * PERCLOS score (0-100).
     * Based on two thresholds from literature:
     * - 0.15 (15%): fatigue onset (PERCLOS optimization study, ACM 2022)
     * - 0.30 (30%): drowsiness (FHWA/NHTSA, Wierwille 1994)
     */
    int computePERCLOSScore(float perclos) {
        if (perclos <= 0) return 0;
        if (perclos < PERCLOS_FATIGUE) {
            // Below fatigue threshold: mild score (0-50)
            float ratio = perclos / PERCLOS_FATIGUE;
            return clamp(Math.round(ratio * 50));
        }
        if (perclos >= PERCLOS_DROWSY) return 100;
        // Between fatigue and drowsy threshold: 50-100
        float ratio = (perclos - PERCLOS_FATIGUE) / (PERCLOS_DROWSY - PERCLOS_FATIGUE);
        return clamp(50 + Math.round(ratio * 50));
    }

    /**
     * Posture fatigue score (0-100).
     * headTiltAngle = neck forward angle (from face pitch + device tilt)
     * shoulderTiltAngle = head roll (side tilt from face landmarks)
     */
    int computePostureFatigueScore(float headTiltAngle, float shoulderTiltAngle) {
        int neckScore = computeNeckAngleScore(headTiltAngle);
        int rollScore = computeHeadRollScore(shoulderTiltAngle);
        return Math.max(neckScore, rollScore);
    }

    int computeNeckAngleScore(float neckAngle) {
        if (neckAngle <= NECK_ANGLE_GOOD) return 0;
        if (neckAngle >= NECK_ANGLE_POOR) return 100;
        float ratio = (neckAngle - NECK_ANGLE_GOOD) / (NECK_ANGLE_POOR - NECK_ANGLE_GOOD);
        return clamp(Math.round(ratio * 100));
    }

    int computeHeadRollScore(float headRoll) {
        if (headRoll <= HEAD_ROLL_GOOD) return 0;
        if (headRoll >= HEAD_ROLL_POOR) return 100;
        float ratio = (headRoll - HEAD_ROLL_GOOD) / (HEAD_ROLL_POOR - HEAD_ROLL_GOOD);
        return clamp(Math.round(ratio * 100));
    }

    int computeCompositeScore(int eyeScore, int postureScore) {
        return clamp(Math.round(alpha * eyeScore + beta * postureScore));
    }

    public int getMovingAverage() {
        if (recentScores.isEmpty()) return 0;
        int sum = 0;
        for (int score : recentScores) {
            sum += score;
        }
        return sum / recentScores.size();
    }

    public void reset() {
        recentScores.clear();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
