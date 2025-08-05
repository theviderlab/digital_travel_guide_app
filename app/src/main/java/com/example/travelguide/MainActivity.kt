package com.example.travelguide

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        arModule = ARModule(surfaceView)
        cameraModule = CameraModule(this) { image ->
            val bitmap = image.toBitmap()
            val detections = inferenceModule.runInference(bitmap)
            arModule.placeMarkers(detections)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            arModule.initializeIfPermitted(this)
            cameraModule.startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CameraModule.REQUEST_CODE_CAMERA_PERMISSION
            )
        }
        arModule.resume()
    }

    override fun onPause() {
        super.onPause()
        cameraModule.stopCamera()
        arModule.pause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CameraModule.REQUEST_CODE_CAMERA_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            arModule.initializeIfPermitted(this)
            arModule.resume()
            cameraModule.startCamera()
        }
    }

}
