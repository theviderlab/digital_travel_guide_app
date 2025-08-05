package com.example.travelguide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageProxy
import com.example.travelguide.ar.ARModule
import com.example.travelguide.camera.CameraModule
import com.example.travelguide.inference.InferenceModule
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var cameraModule: CameraModule
    private lateinit var arModule: ARModule
    private lateinit var inferenceModule: InferenceModule
    private lateinit var surfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = GLSurfaceView(this)
        setContentView(surfaceView)

        inferenceModule = InferenceModule(this)
        arModule = ARModule(this, surfaceView)
        cameraModule = CameraModule(this) { image ->
            val bitmap = image.toBitmap()
            val detections = inferenceModule.runInference(bitmap)
            arModule.placeMarkers(detections)
        }
    }

    override fun onResume() {
        super.onResume()
        arModule.resume()
        cameraModule.startCamera()
    }

    override fun onPause() {
        super.onPause()
        cameraModule.stopCamera()
        arModule.pause()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val yuv = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }
}
