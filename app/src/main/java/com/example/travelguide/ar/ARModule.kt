package com.example.travelguide.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.travelguide.inference.Detection
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Minimal ARCore utility that manages a [Session], a rendering surface and
 * allows placing simple markers based on model detections.
 */
class ARModule(
    private val surfaceView: GLSurfaceView
) : GLSurfaceView.Renderer {
    private var session: Session? = null
    private var frame: Frame? = null
    private val anchors = mutableListOf<Marker>()
    private var width = 0
    private var height = 0
    private var lastPose: Pose? = null

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
        session = try {
            Session(context).apply {
                val config = Config(this)
                configure(config)
            }
        } catch (e: UnavailableException) {
            null
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
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
        try {
            session?.resume()
        } catch (_: CameraNotAvailableException) {
            // Ignored for brevity
        }
        surfaceView.onResume()
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

