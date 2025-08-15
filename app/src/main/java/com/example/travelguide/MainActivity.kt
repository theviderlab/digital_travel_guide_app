package com.example.travelguide

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import com.example.travelguide.ar.ARModule
import com.example.travelguide.camera.CameraModule
import com.example.travelguide.inference.InferenceModule

class MainActivity : ComponentActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var arModule: ARModule
    private lateinit var inferenceModule: InferenceModule
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var cameraModule: CameraModule? = null
    private var previewView: PreviewView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        glSurfaceView = findViewById(R.id.glSurfaceView)
        inferenceModule = InferenceModule(this)
        arModule = ARModule(glSurfaceView, inferenceModule)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startArOrFallback()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startArOrFallback()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        super.onPause()
        arModule.pause()
        cameraModule?.stopCamera()
    }

    private fun startArOrFallback() {
        arModule.initializeIfPermitted(this)
        val container = findViewById<FrameLayout>(R.id.container)
        if (arModule.hasSession()) {
            previewView?.let {
                container.removeView(it)
                cameraModule?.stopCamera()
                cameraModule = null
                previewView = null
            }
            arModule.resume()
        } else {
            if (previewView == null) {
                previewView = PreviewView(this)
                container.addView(previewView)
                cameraModule = CameraModule(this, previewView!!)
                Toast.makeText(
                    this,
                    "La experiencia RA requiere ARCore; se muestra la vista básica de cámara",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
