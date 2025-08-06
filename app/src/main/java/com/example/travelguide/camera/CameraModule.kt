package com.example.travelguide.camera

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.util.Log
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
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Starts the rear camera if permissions are granted. */
    fun startCamera() {
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
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also {
                it.setAnalyzer(cameraExecutor, ThrottledAnalyzer(frameCallback))
            }

        try {
            provider.unbindAll()
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Camera already disconnected: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind previous use cases", e)
        }
        provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
    }

    /** Stops the camera and releases resources. */
    fun stopCamera() {
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Camera already disconnected: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        } finally {
            cameraExecutor.shutdown()
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
    }
}
