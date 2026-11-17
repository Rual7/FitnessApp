package com.fitnessapp.cv.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ConcurrentHashMap

/**
 * MediaPipe Pose Landmarker in LIVE_STREAM mode: frames go in with [submit], results come back
 * asynchronously on a MediaPipe thread through the [PoseResultListener]. When inference is slower
 * than the camera, MediaPipe drops frames instead of queueing them.
 */
class MediaPipePoseEngine(
    context: Context,
    private val config: Config = Config(),
) : PoseEngine {

    data class Config(
        val modelAsset: String = "pose_landmarker_lite.task",
        val useGpu: Boolean = false,
        val numPoses: Int = 1,
        val minDetectionConfidence: Float = 0.5f,
        val minPresenceConfidence: Float = 0.5f,
        val minTrackingConfidence: Float = 0.5f,
    )

    private class Pending(val submittedAtMs: Long, val width: Int, val height: Int)

    @Volatile
    private var listener: PoseResultListener? = null

    @Volatile
    private var closed = false
    private val pending = ConcurrentHashMap<Long, Pending>()
    private var lastTimestampMs = 0L
    private val landmarker: PoseLandmarker

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(config.modelAsset)
            .setDelegate(if (config.useGpu) Delegate.GPU else Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(config.numPoses)
            .setMinPoseDetectionConfidence(config.minDetectionConfidence)
            .setMinPosePresenceConfidence(config.minPresenceConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { /* a failed frame is simply skipped */ }
            .build()
        landmarker = PoseLandmarker.createFromOptions(context.applicationContext, options)
    }

    override fun setListener(listener: PoseResultListener?) {
        this.listener = listener
    }

    /**
     * Submits one camera frame (RGBA). Takes ownership of [image] and closes it. Frames that arrive
     * after [close] (still queued on the analysis thread when the screen is left) are dropped.
     */
    fun submit(image: ImageProxy) {
        if (closed) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val raw = image.toBitmap()
        image.close()

        // CameraX reports the clockwise rotation that makes the frame upright. It is applied here
        // (not through MediaPipe's ImageProcessingOptions, whose rotation is counter-clockwise),
        // so the landmarks come back in the coordinates of the upright image.
        val bitmap = if (rotation == 0) {
            raw
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }

        // MediaPipe requires strictly increasing timestamps.
        val now = SystemClock.uptimeMillis()
        val timestamp = if (now > lastTimestampMs) now else lastTimestampMs + 1
        lastTimestampMs = timestamp

        pending[timestamp] = Pending(now, bitmap.width, bitmap.height)
        try {
            landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestamp)
        } catch (e: IllegalStateException) {
            pending.remove(timestamp) // the landmarker was closed between the check and the call
        }
    }

    private fun handleResult(result: PoseLandmarkerResult) {
        val timestamp = result.timestampMs()
        val info = pending.remove(timestamp) ?: return
        // Frames dropped by MediaPipe never produce a result: forget them.
        pending.keys.removeAll { it < timestamp }

        val landmarks = result.landmarks().firstOrNull()?.map {
            Landmark(
                x = it.x(),
                y = it.y(),
                z = it.z(),
                visibility = it.visibility().orElse(0f),
                presence = it.presence().orElse(0f),
            )
        }
        val frame = PoseFrame(
            timestampMs = timestamp,
            imageWidth = info.width,
            imageHeight = info.height,
            landmarks = landmarks,
            inferenceMs = SystemClock.uptimeMillis() - info.submittedAtMs,
        )
        listener?.onResult(frame)
    }

    override fun close() {
        closed = true
        listener = null
        landmarker.close()
    }
}
