package com.example.travelguide

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.travelguide.ar.ARModule
import com.example.travelguide.inference.InferenceModule

class MainActivity : ComponentActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var arModule: ARModule
    private lateinit var inferenceModule: InferenceModule
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        glSurfaceView = findViewById(R.id.glSurfaceView)
        inferenceModule = InferenceModule(this)
        arModule = ARModule(glSurfaceView, inferenceModule)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    arModule.initializeIfPermitted(this)
                    arModule.resume()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            arModule.initializeIfPermitted(this)
            arModule.resume()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        super.onPause()
        arModule.pause()
    }
}
