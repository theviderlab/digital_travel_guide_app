package com.example.travelguide.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Loads an ONNX model from assets and provides image inference utilities.
 */
class InferenceModule(context: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession? = try {
        val model = context.assets.open("pipeline.onnx").use { it.readBytes() }
        env.createSession(model)
    } catch (e: Exception) {
        null
    }
    private val placesDbTensor: OnnxTensor?
    private val labelMap: Map<Int, String>

    init {
        placesDbTensor = try {
            val bytes = context.assets.open("places_db.bin").use { it.readBytes() }
            val floatBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val floats = FloatArray(floatBuffer.remaining())
            floatBuffer.get(floats)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), longArrayOf(floats.size.toLong()))
        } catch (e: Exception) {
            null
        }

        labelMap = try {
            context.assets.open("label_map.json").use { stream ->
                val json = JSONObject(String(stream.readBytes()))
                val map = mutableMapOf<Int, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key.toInt()] = json.getString(key)
                }
                map
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Runs inference on [bitmap] and returns a list of [Detection] results.
     */
    fun runInference(bitmap: Bitmap): List<Detection> {
        val ortSession = session ?: return emptyList()

        val width = bitmap.width
        val height = bitmap.height
        val inputData = FloatArray(3 * width * height)
        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                inputData[idx++] = ((pixel shr 16) and 0xFF) / 255f
                inputData[idx++] = ((pixel shr 8) and 0xFF) / 255f
                inputData[idx++] = (pixel and 0xFF) / 255f
            }
        }

        val inputName = ortSession.inputNames.iterator().next()
        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, 3, height.toLong(), width.toLong())
        ).use { tensor ->
            ortSession.run(mapOf(inputName to tensor)).use { result ->
                val boxes = result[0].value as Array<FloatArray>
                val scores = result[1].value as FloatArray
                val classes = result[2].value as FloatArray

                val count = min(boxes.size, min(scores.size, classes.size))
                val detections = mutableListOf<Detection>()
                for (i in 0 until count) {
                    val b = boxes[i]
                    val rect = Rect(
                        (b[0] * width).toInt(),
                        (b[1] * height).toInt(),
                        (b[2] * width).toInt(),
                        (b[3] * height).toInt()
                    )
                    val label = labelMap[classes[i].toInt()] ?: classes[i].toInt().toString()
                    detections.add(
                        Detection(rect, label, scores[i])
                    )
                }
                return detections
            }
        }
    }
}

/** Data class describing a single model detection. */
data class Detection(val box: Rect, val label: String, val score: Float)
