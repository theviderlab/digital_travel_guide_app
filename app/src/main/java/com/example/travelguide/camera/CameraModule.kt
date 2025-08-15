package com.example.travelguide.camera

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Utility class that configures the rear camera using CameraX and exposes
 * frames roughly once per second via [frameCallback].
 */
@Suppress("unused")
class CameraModule(
    private val activity: ComponentActivity,
    private val previewView: PreviewView,
    private val frameCallback: ((ImageProxy) -> Unit)? = null
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    // Executor used for image analysis; recreated as needed between start/stop cycles
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0

    init {
        startCamera()
    }

    /** Starts the rear camera if permissions are granted. */
    private fun startCamera() {
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
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(activity))
    }

    /** Binds [Preview] and optional [ImageAnalysis] use cases. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val executor = cameraExecutor

        preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val analysis = if (frameCallback != null) {
            val builder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            Camera2Interop.Extender(builder).setDeviceStateCallback(object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {}

                override fun onDisconnected(camera: CameraDevice) {
                    handleCameraDisconnected(Exception("Camera device disconnected"))
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                }
            })

            builder.build().also {
                it.setAnalyzer(executor, ThrottledAnalyzer(frameCallback))
            }
        } else {
            null
        }

        try {
            provider.unbindAll()
            val useCases = mutableListOf<UseCase>(preview!!)
            analysis?.let { useCases.add(it) }
            provider.bindToLifecycle(
                activity,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases.toTypedArray()
            )
            reconnectAttempts = 0
        } catch (e: CameraAccessException) {
            if (e.reason == CameraAccessException.CAMERA_DISCONNECTED) {
                handleCameraDisconnected(e)
            } else {
                Log.e(TAG, "Failed to bind camera use cases", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /** Stops the camera and releases resources. */
    @Suppress("unused")
    fun stopCamera() {
        try {
            activity.runOnUiThread {
                imageAnalysis?.clearAnalyzer()
                cameraProvider?.unbindAll()
            }
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
            preview = null
        }
    }

    private fun handleCameraDisconnected(e: Exception) {
        Log.w(
            TAG,
            "Camera disconnected (CameraAccessException.CAMERA_DISCONNECTED): ${e.message}"
        )
        activity.runOnUiThread {
            releaseResources()
            scheduleReconnect()
        }
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
            activity.runOnUiThread {
                imageAnalysis?.clearAnalyzer()
                cameraProvider?.unbindAll()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing camera resources: ${e.message}")
        } finally {
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
            cameraProvider = null
            imageAnalysis = null
            preview = null
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
