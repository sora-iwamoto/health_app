package com.nimura.app.detection;

public class DetectionResult {

    public final long timestamp;
    public final float earLeft;
    public final float earRight;
    public final float earAvg;
    public final boolean isBlinkDetected;
    public final float headTiltAngle;
    public final float shoulderTiltAngle;
    public final boolean faceDetected;
    public final boolean poseDetected;

    public DetectionResult(long timestamp, float earLeft, float earRight, float earAvg,
                           boolean isBlinkDetected, float headTiltAngle, float shoulderTiltAngle,
                           boolean faceDetected, boolean poseDetected) {
        this.timestamp = timestamp;
        this.earLeft = earLeft;
        this.earRight = earRight;
        this.earAvg = earAvg;
        this.isBlinkDetected = isBlinkDetected;
        this.headTiltAngle = headTiltAngle;
        this.shoulderTiltAngle = shoulderTiltAngle;
        this.faceDetected = faceDetected;
        this.poseDetected = poseDetected;
    }
}
