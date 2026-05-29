package com.nimura.app.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraManager {

    private static final String TAG = "CameraManager";

    private final Context context;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private final ExecutorService analysisExecutor;
    private FrameListener frameListener;
    private volatile boolean isAnalysisEnabled = true;
    private long frameCount = 0;

    public interface FrameListener {
        void onFrame(MPImage image, long timestampMs);
    }

    public CameraManager(Context context) {
        this.context = context;
        this.analysisExecutor = Executors.newSingleThreadExecutor();
    }

    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    public void setAnalysisEnabled(boolean enabled) {
        this.isAnalysisEnabled = enabled;
        if (enabled) {
            frameCount = 0;
        }
    }

    public void startCamera(LifecycleOwner lifecycleOwner) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(lifecycleOwner);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases(LifecycleOwner lifecycleOwner) {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeImage);

        try {
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis);
            Log.d(TAG, "Camera bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera use cases", e);
        }
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (!isAnalysisEnabled || frameListener == null) {
            imageProxy.close();
            return;
        }

        try {
            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            int rowStride = imageProxy.getPlanes()[0].getRowStride();
            int pixelStride = imageProxy.getPlanes()[0].getPixelStride();

            ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
            buffer.rewind();

            Bitmap bitmap;

            if (rowStride == width * pixelStride) {
                // No padding — can copy directly
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
            } else {
                // Row stride has padding — copy row by row
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                byte[] rowData = new byte[rowStride];
                int[] pixels = new int[width];
                for (int y = 0; y < height; y++) {
                    buffer.get(rowData, 0, rowStride);
                    for (int x = 0; x < width; x++) {
                        int offset = x * pixelStride;
                        int r = rowData[offset] & 0xFF;
                        int g = rowData[offset + 1] & 0xFF;
                        int b = rowData[offset + 2] & 0xFF;
                        int a = rowData[offset + 3] & 0xFF;
                        pixels[x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    bitmap.setPixels(pixels, 0, width, 0, y, width, 1);
                }
            }

            // Apply rotation and mirror for front camera
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                bitmap.recycle();
                bitmap = rotated;
            }

            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            long timestampMs = System.currentTimeMillis();

            frameListener.onFrame(mpImage, timestampMs);

            frameCount++;
            if (frameCount <= 3 || frameCount % 30 == 1) {
                Log.d(TAG, "Frame #" + frameCount +
                        " (" + bitmap.getWidth() + "x" + bitmap.getHeight() +
                        ", rotation=" + rotation +
                        ", rowStride=" + rowStride +
                        ", expected=" + (width * pixelStride) + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image: " + e.getMessage(), e);
        } finally {
            imageProxy.close();
        }
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    public void close() {
        stopCamera();
        analysisExecutor.shutdown();
    }
}
