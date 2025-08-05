package com.example.travelguide

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.travelguide.ar.ARModule
import com.example.travelguide.camera.CameraModule
import com.example.travelguide.inference.InferenceModule

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

}
