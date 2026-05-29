package com.nimura.app.detection;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

public class PoseLandmarkerHelper {

    private static final String TAG = "PoseLandmarkerHelper";
    private static final String MODEL_NAME = "pose_landmarker_lite.task";

    private static final int NOSE = 0;
    private static final int LEFT_SHOULDER = 11;
    private static final int RIGHT_SHOULDER = 12;

    private PoseLandmarker poseLandmarker;
    private ResultListener resultListener;

    public interface ResultListener {
        void onPoseResult(float headTiltAngle, float shoulderTiltAngle);
        void onPoseError(String error);
    }

    public PoseLandmarkerHelper(Context context, ResultListener listener) {
        this.resultListener = listener;
        setupPoseLandmarker(context);
    }

    private void setupPoseLandmarker(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_NAME)
                    .build();

            PoseLandmarker.PoseLandmarkerOptions options =
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setNumPoses(1)
                            .setMinPoseDetectionConfidence(0.5f)
                            .setMinPosePresenceConfidence(0.5f)
                            .setMinTrackingConfidence(0.5f)
                            .setResultListener(this::handleResult)
                            .setErrorListener(this::handleError)
                            .build();

            poseLandmarker = PoseLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PoseLandmarker", e);
            if (resultListener != null) {
                resultListener.onPoseError("PoseLandmarker initialization failed: " + e.getMessage());
            }
        }
    }

    public void detectAsync(MPImage image, long timestampMs) {
        if (poseLandmarker == null) {
            Log.w(TAG, "poseLandmarker is null, skipping detection");
            return;
        }
        poseLandmarker.detectAsync(image, timestampMs);
    }

    private void handleResult(PoseLandmarkerResult result, MPImage input) {
        if (result == null) {
            Log.d(TAG, "Pose result is null");
            return;
        }
        if (result.landmarks().isEmpty()) {
            Log.d(TAG, "No pose detected");
            return;
        }
        Log.d(TAG, "Pose detected! Landmarks: " + result.landmarks().get(0).size());

        List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks =
                result.landmarks().get(0);

        float headTiltAngle = calculateHeadTilt(landmarks);
        float shoulderTiltAngle = calculateShoulderTilt(landmarks);

        if (resultListener != null) {
            resultListener.onPoseResult(headTiltAngle, shoulderTiltAngle);
        }
    }

    private void handleError(RuntimeException e) {
        Log.e(TAG, "PoseLandmarker error", e);
        if (resultListener != null) {
            resultListener.onPoseError(e.getMessage());
        }
    }

    /**
     * Calculate head forward tilt angle.
     * Measures the angle between the nose and the midpoint of shoulders
     * relative to the vertical axis.
     * A larger angle indicates more forward lean (worse posture).
     */
    private float calculateHeadTilt(
            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks) {

        float noseX = landmarks.get(NOSE).x();
        float noseY = landmarks.get(NOSE).y();
        float leftShoulderX = landmarks.get(LEFT_SHOULDER).x();
        float leftShoulderY = landmarks.get(LEFT_SHOULDER).y();
        float rightShoulderX = landmarks.get(RIGHT_SHOULDER).x();
        float rightShoulderY = landmarks.get(RIGHT_SHOULDER).y();

        float shoulderMidX = (leftShoulderX + rightShoulderX) / 2.0f;
        float shoulderMidY = (leftShoulderY + rightShoulderY) / 2.0f;

        // Vector from shoulder midpoint to nose
        float dx = noseX - shoulderMidX;
        float dy = shoulderMidY - noseY; // Y is inverted in screen coordinates

        // Angle from vertical (0 = perfectly upright)
        float angleRad = (float) Math.atan2(Math.abs(dx), dy);
        return (float) Math.toDegrees(angleRad);
    }

    /**
     * Calculate shoulder tilt angle.
     * Measures the angle of the line between left and right shoulders
     * relative to horizontal.
     * A larger angle indicates more tilt (one shoulder higher than the other).
     */
    private float calculateShoulderTilt(
            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks) {

        float leftY = landmarks.get(LEFT_SHOULDER).y();
        float rightY = landmarks.get(RIGHT_SHOULDER).y();
        float leftX = landmarks.get(LEFT_SHOULDER).x();
        float rightX = landmarks.get(RIGHT_SHOULDER).x();

        float dy = Math.abs(leftY - rightY);
        float dx = Math.abs(leftX - rightX);

        if (dx == 0) return 0;
        float angleRad = (float) Math.atan2(dy, dx);
        return (float) Math.toDegrees(angleRad);
    }

    public void close() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
    }
}
