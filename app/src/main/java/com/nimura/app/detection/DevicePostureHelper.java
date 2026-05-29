package com.nimura.app.detection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Measures device tilt angle using the accelerometer (IMU sensor).
 *
 * When the user holds the phone to look at the screen, the phone's tilt angle
 * correlates with the user's neck angle. A phone held nearly vertical (90 deg)
 * suggests looking straight ahead, while a phone tilted flat (0 deg) suggests
 * looking down with a bent neck.
 *
 * Combined with face landmark head pitch, this gives a more accurate
 * estimate of the user's actual neck posture.
 */
public class DevicePostureHelper implements SensorEventListener {

    private static final String TAG = "DevicePostureHelper";

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private volatile float deviceTiltAngle = 90.0f; // degrees from horizontal
    private volatile float deviceRollAngle = 0.0f;  // degrees of side tilt
    private DevicePostureListener listener;

    public interface DevicePostureListener {
        void onDeviceTiltUpdate(float tiltAngle, float rollAngle);
    }

    public DevicePostureHelper(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager != null ?
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        if (accelerometer == null) {
            Log.w(TAG, "Accelerometer not available on this device");
        }
    }

    public void setListener(DevicePostureListener listener) {
        this.listener = listener;
    }

    public void startListening() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "Accelerometer listening started");
        }
    }

    public void stopListening() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.d(TAG, "Accelerometer listening stopped");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0]; // lateral
        float y = event.values[1]; // vertical (along phone length)
        float z = event.values[2]; // perpendicular to screen

        // Device tilt: angle between phone's screen-normal and vertical
        // When phone is vertical (held up to face): y is large, z is small → tilt ≈ 90
        // When phone is flat on table: y is small, z is large → tilt ≈ 0
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (magnitude == 0) return;

        // Tilt angle from horizontal (0 = flat, 90 = vertical)
        deviceTiltAngle = (float) Math.toDegrees(Math.asin(
                Math.min(1.0f, Math.abs(y) / magnitude)));

        // Roll angle (side tilt of device)
        deviceRollAngle = (float) Math.toDegrees(Math.atan2(x, y));

        if (listener != null) {
            listener.onDeviceTiltUpdate(deviceTiltAngle, deviceRollAngle);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    /**
     * Get the current device tilt angle from horizontal.
     * 90 = phone held vertically (good posture)
     * 0 = phone flat (looking straight down, bad posture)
     */
    public float getDeviceTiltAngle() {
        return deviceTiltAngle;
    }

    /**
     * Get the device roll angle.
     */
    public float getDeviceRollAngle() {
        return deviceRollAngle;
    }
}
