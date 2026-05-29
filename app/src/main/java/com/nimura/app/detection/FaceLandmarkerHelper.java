package com.nimura.app.detection;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.List;

public class FaceLandmarkerHelper {

    private static final String TAG = "FaceLandmarkerHelper";
    private static final String MODEL_NAME = "face_landmarker.task";
    // EAR threshold is now managed by BlinkTracker (supports calibration)
    // This is only used for the isBlinkDetected hint in the callback
    private static final float EAR_BLINK_HINT_THRESHOLD = 0.2f;

    // Left eye landmark indices for EAR
    private static final int LEFT_EYE_P1 = 33;
    private static final int LEFT_EYE_P2 = 160;
    private static final int LEFT_EYE_P3 = 158;
    private static final int LEFT_EYE_P4 = 133;
    private static final int LEFT_EYE_P5 = 153;
    private static final int LEFT_EYE_P6 = 144;

    // Right eye landmark indices for EAR (matched to reference project order)
    private static final int RIGHT_EYE_P1 = 362;
    private static final int RIGHT_EYE_P2 = 385;
    private static final int RIGHT_EYE_P3 = 387;
    private static final int RIGHT_EYE_P4 = 263;
    private static final int RIGHT_EYE_P5 = 373;
    private static final int RIGHT_EYE_P6 = 380;

    // Image dimensions for pixel-scale EAR (set from outside)
    private int imageWidth = 480;
    private int imageHeight = 640;

    // Head pose landmarks (stable points for solvePnP-like estimation)
    private static final int NOSE_TIP = 1;
    private static final int CHIN = 152;
    private static final int LEFT_EYE_OUTER = 33;
    private static final int RIGHT_EYE_OUTER = 263;
    private static final int LEFT_MOUTH = 61;
    private static final int RIGHT_MOUTH = 291;
    private static final int FOREHEAD = 10;

    private FaceLandmarker faceLandmarker;
    private ResultListener resultListener;

    public interface ResultListener {
        void onFaceResult(float earLeft, float earRight, float earAvg, boolean isBlinkDetected,
                          float headPitch, float headRoll);
        void onFaceError(String error);
    }

    public FaceLandmarkerHelper(Context context, ResultListener listener) {
        this.resultListener = listener;
        setupFaceLandmarker(context);
    }

    private void setupFaceLandmarker(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_NAME)
                    .build();

            FaceLandmarker.FaceLandmarkerOptions options =
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.LIVE_STREAM)
                            .setNumFaces(1)
                            .setMinFaceDetectionConfidence(0.5f)
                            .setMinFacePresenceConfidence(0.5f)
                            .setMinTrackingConfidence(0.5f)
                            .setResultListener(this::handleResult)
                            .setErrorListener(this::handleError)
                            .build();

            faceLandmarker = FaceLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize FaceLandmarker", e);
            if (resultListener != null) {
                resultListener.onFaceError("FaceLandmarker initialization failed: " + e.getMessage());
            }
        }
    }

    public void setImageDimensions(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
    }

    public void detectAsync(MPImage image, long timestampMs) {
        if (faceLandmarker == null) {
            Log.w(TAG, "faceLandmarker is null, skipping detection");
            return;
        }
        faceLandmarker.detectAsync(image, timestampMs);
    }

    private void handleResult(FaceLandmarkerResult result, MPImage input) {
        if (result == null) {
            Log.d(TAG, "Face result is null");
            return;
        }
        if (result.faceLandmarks().isEmpty()) {
            Log.d(TAG, "No face detected");
            return;
        }

        List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks =
                result.faceLandmarks().get(0);

        // EAR calculation
        float earLeft = calculateEAR(landmarks,
                LEFT_EYE_P1, LEFT_EYE_P2, LEFT_EYE_P3,
                LEFT_EYE_P4, LEFT_EYE_P5, LEFT_EYE_P6);

        float earRight = calculateEAR(landmarks,
                RIGHT_EYE_P1, RIGHT_EYE_P2, RIGHT_EYE_P3,
                RIGHT_EYE_P4, RIGHT_EYE_P5, RIGHT_EYE_P6);

        float earAvg = (earLeft + earRight) / 2.0f;
        boolean isBlinkDetected = earAvg < EAR_BLINK_HINT_THRESHOLD;

        // Head pose from face landmarks
        float headPitch = calculateHeadPitch(landmarks);
        float headRoll = calculateHeadRoll(landmarks);

        if (resultListener != null) {
            resultListener.onFaceResult(earLeft, earRight, earAvg, isBlinkDetected,
                    headPitch, headRoll);
        }
    }

    private void handleError(RuntimeException e) {
        Log.e(TAG, "FaceLandmarker error", e);
        if (resultListener != null) {
            resultListener.onFaceError(e.getMessage());
        }
    }

    /**
     * Estimate head pitch (forward tilt) from face landmarks.
     *
     * Uses the vertical relationship between forehead (index 10), nose tip (index 1),
     * and chin (index 152). When the head tilts forward, the nose tip moves down
     * relative to the forehead-chin midpoint.
     *
     * Returns degrees: 0 = looking straight, positive = looking down (forward tilt).
     */
    private float calculateHeadPitch(
            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks) {

        float foreheadY = landmarks.get(FOREHEAD).y();
        float noseTipY = landmarks.get(NOSE_TIP).y();
        float chinY = landmarks.get(CHIN).y();

        // Face height as reference
        float faceHeight = chinY - foreheadY;
        if (faceHeight <= 0) return 0;

        // Expected nose position = midpoint between forehead and chin
        float expectedNoseY = foreheadY + faceHeight * 0.4f; // nose is ~40% down from forehead
        float deviation = (noseTipY - expectedNoseY) / faceHeight;

        // Also use the ratio of forehead-to-nose vs nose-to-chin
        float upperFace = noseTipY - foreheadY;
        float lowerFace = chinY - noseTipY;

        // When looking down: upper face gets shorter, lower face gets longer
        // Normal ratio: upperFace/lowerFace ≈ 0.65-0.75
        float ratio = (faceHeight > 0) ? upperFace / faceHeight : 0.5f;

        // Convert to approximate pitch angle
        // ratio ~0.4 = looking straight, ~0.3 = looking down, ~0.5 = looking up
        float pitchDegrees = (ratio - 0.4f) * -150.0f; // negative because higher ratio = looking up

        return pitchDegrees;
    }

    /**
     * Estimate head roll (side tilt) from face landmarks.
     *
     * Uses the angle of the line between left and right eye outer corners.
     * When the head tilts to one side, this line is no longer horizontal.
     *
     * Returns degrees: 0 = level, positive = tilting right.
     */
    private float calculateHeadRoll(
            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks) {

        float leftEyeX = landmarks.get(LEFT_EYE_OUTER).x();
        float leftEyeY = landmarks.get(LEFT_EYE_OUTER).y();
        float rightEyeX = landmarks.get(RIGHT_EYE_OUTER).x();
        float rightEyeY = landmarks.get(RIGHT_EYE_OUTER).y();

        float dx = rightEyeX - leftEyeX;
        float dy = rightEyeY - leftEyeY;

        float angleRad = (float) Math.atan2(dy, dx);
        return (float) Math.toDegrees(angleRad);
    }

    private float calculateEAR(
            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks,
            int p1, int p2, int p3, int p4, int p5, int p6) {

        float a = distance(landmarks.get(p2), landmarks.get(p6));
        float b = distance(landmarks.get(p3), landmarks.get(p5));
        float c = distance(landmarks.get(p1), landmarks.get(p4));

        if (c == 0) return 0;
        return (a + b) / (2.0f * c);
    }

    /**
     * Pixel-scale distance calculation (reference project pattern).
     * Multiplies normalized coordinates by image dimensions to correct for
     * aspect ratio distortion in EAR calculation.
     */
    private float distance(
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark a,
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark b) {
        float dx = (a.x() - b.x()) * imageWidth;
        float dy = (a.y() - b.y()) * imageHeight;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public void close() {
        if (faceLandmarker != null) {
            faceLandmarker.close();
            faceLandmarker = null;
        }
    }
}
