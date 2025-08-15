package com.example.travelguide.ar

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.travelguide.inference.Detection
import com.example.travelguide.inference.InferenceModule
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import android.util.Log
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt
import java.io.ByteArrayOutputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Minimal ARCore utility that manages a [Session], a rendering surface and
 * allows placing simple markers based on model detections.
 */
class ARModule(
    private val surfaceView: GLSurfaceView,
    private val inference: InferenceModule? = null
) : GLSurfaceView.Renderer {
    private var session: Session? = null
    private var frame: Frame? = null
    private val anchors = mutableListOf<Marker>()
    private var width = 0
    private var height = 0
    private var lastPose: Pose? = null
    private val backgroundRenderer = BackgroundRenderer()
    private var pendingResume = false
    private var lastInferenceTime = 0L

    init {
        surfaceView.setEGLContextClientVersion(3)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    /**
     * Lazily creates the ARCore [Session] if camera permissions were granted.
     */
    fun initializeIfPermitted(context: Context) {
        if (session != null) return
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability != ArCoreApk.Availability.INSTALLED) {
            if (context is Activity) {
                try {
                    ArCoreApk.getInstance().requestInstall(context, true)
                } catch (e: UnavailableException) {
                    Log.e("ARModule", "ARCore install request failed", e)
                }
            }
            Toast.makeText(
                context,
                "Instala o actualiza ARCore para continuar",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        session = try {
            Session(context).apply {
                val config = Config(this)
                configure(config)
            }
        } catch (e: UnavailableException) {
            Log.e("ARModule", "Failed to create ARCore session", e)
            null
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread()
        val texId = backgroundRenderer.textureId
        if (texId != -1) {
            session?.setCameraTextureName(texId)
            if (pendingResume) {
                try {
                    session?.resume()
                } catch (_: CameraNotAvailableException) {
                }
                pendingResume = false
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val session = session ?: return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            return
        }
        this.frame = frame
        backgroundRenderer.draw(frame)
        processFrameForInference(frame)
        val pose = frame.camera.pose
        if (hasMovedAbruptly(pose)) {
            lastPose = pose
        }
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        frame.camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        frame.camera.getViewMatrix(viewMatrix, 0)
        anchors.forEach { it.draw(viewMatrix, projectionMatrix) }
    }

    private fun processFrameForInference(frame: Frame) {
        val inference = inference ?: return
        val now = System.currentTimeMillis()
        if (now - lastInferenceTime < 1000) return
        val image = try {
            frame.acquireCameraImage()
        } catch (_: NotYetAvailableException) {
            return
        } catch (_: Exception) {
            return
        }
        try {
            val bitmap = image.toBitmap()
            val detections = inference.runInference(bitmap)
            placeMarkers(detections)
        } finally {
            image.close()
        }
        lastInferenceTime = now
    }

    /**
     * Places 3D markers at the center of each detection's bounding box.
     * Markers are recomputed only when the camera pose changes drastically.
     */
    fun placeMarkers(detections: List<Detection>) {
        session ?: return
        val frame = frame ?: return
        val pose = frame.camera.pose
        if (!hasMovedAbruptly(pose)) {
            return
        }

        anchors.forEach { it.anchor.detach() }
        anchors.clear()
        for (d in detections) {
            val cx = (d.box.left + d.box.right) / 2f
            val cy = (d.box.top + d.box.bottom) / 2f
            val hit = frame.hitTest(cx, cy).firstOrNull() ?: continue
            val anchor = hit.createAnchor()
            anchors += Marker(anchor, d.label)
        }
        lastPose = pose
    }

    /** Resumes the ARCore session and rendering surface. */
    fun resume() {
        surfaceView.onResume()
        val texId = backgroundRenderer.textureId
        if (texId != -1) {
            try {
                session?.resume()
            } catch (_: CameraNotAvailableException) {
                // Ignored for brevity
            }
        } else {
            pendingResume = true
        }
    }

    /** Pauses the rendering surface and ARCore session. */
    fun pause() {
        surfaceView.onPause()
        session?.pause()
    }

    private fun hasMovedAbruptly(newPose: Pose): Boolean {
        val prev = lastPose ?: return true
        val dx = newPose.tx() - prev.tx()
        val dy = newPose.ty() - prev.ty()
        val dz = newPose.tz() - prev.tz()
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val q1 = newPose.rotationQuaternion
        val q2 = prev.rotationQuaternion
        val dot = q1[0] * q2[0] + q1[1] * q2[1] + q1[2] * q2[2] + q1[3] * q2[3]
        val angle = (2 * acos(min(1f, abs(dot))) * 180f / PI.toFloat())
        return dist > 0.1f || angle > 10f
    }

    private fun Image.toBitmap(): Bitmap {
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
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private class Marker(val anchor: Anchor, val label: String) {
        private val matrix = FloatArray(16)
        fun draw(viewMatrix: FloatArray, projMatrix: FloatArray) {
            anchor.pose.toMatrix(matrix, 0)
            val modelView = FloatArray(16)
            val mvp = FloatArray(16)
            Matrix.multiplyMM(modelView, 0, viewMatrix, 0, matrix, 0)
            Matrix.multiplyMM(mvp, 0, projMatrix, 0, modelView, 0)
            // Placeholder: here a 3D marker and text label would be rendered
            // using [mvp] matrix and [label].
        }
    }
}

