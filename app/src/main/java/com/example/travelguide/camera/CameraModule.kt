package com.example.travelguide.camera

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Utility class that configures the rear camera using CameraX and exposes
 * frames roughly once per second via [frameCallback].
 */
class CameraModule(
    private val activity: ComponentActivity,
    private val frameCallback: (ImageProxy) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    // Executor used for image analysis; recreated as needed between start/stop cycles
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0

    /** Starts the rear camera if permissions are granted. */
    fun startCamera() {
        // Re-create the executor if it was previously shut down
        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindAnalysisUseCase()
        }, ContextCompat.getMainExecutor(activity))
    }

    /** Binds an [ImageAnalysis] use case that throttles frames to ~1 fps. */
    private fun bindAnalysisUseCase() {
        val provider = cameraProvider ?: return
        val executor = cameraExecutor
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also {
                it.setAnalyzer(executor, ThrottledAnalyzer(frameCallback))
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
            reconnectAttempts = 0
        } catch (e: CameraAccessException) {
            if (e.reason == CameraAccessException.CAMERA_DISCONNECTED) {
                handleCameraDisconnected(e)
            } else {
                Log.e(TAG, "Failed to bind analysis use case", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind analysis use case", e)
        }
    }

    /** Stops the camera and releases resources. */
    fun stopCamera() {
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (e: CameraAccessException) {
            if (e.reason == CameraAccessException.CAMERA_DISCONNECTED) {
                handleCameraDisconnected(e)
            } else {
                Log.e(TAG, "Error stopping camera", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        } finally {
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            cameraProvider = null
            imageAnalysis = null
        }
    }

    private fun handleCameraDisconnected(e: Exception) {
        Log.w(TAG, "Camera disconnected: ${e.message}")
        releaseResources()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            handler.postDelayed({ startCamera() }, RECONNECT_DELAY_MS)
        } else {
            activity.runOnUiThread {
                Toast.makeText(activity, "No se pudo reconectar la cámara", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun releaseResources() {
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing camera resources: ${e.message}")
        } finally {
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            cameraProvider = null
            imageAnalysis = null
        }
    }

    private class ThrottledAnalyzer(
        private val callback: (ImageProxy) -> Unit
    ) : ImageAnalysis.Analyzer {
        private var lastTimestamp = 0L

        override fun analyze(image: ImageProxy) {
            val now = System.currentTimeMillis()
            if (now - lastTimestamp >= 1000) {
                callback(image)
                lastTimestamp = now
            }
            image.close()
        }
    }

    companion object {
        const val REQUEST_CODE_CAMERA_PERMISSION = 1001
        private const val TAG = "CameraModule"
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_DELAY_MS = 500L
    }
}
