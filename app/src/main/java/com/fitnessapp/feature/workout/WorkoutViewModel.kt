package com.fitnessapp.feature.workout

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import com.fitnessapp.cv.engine.BicepCurlTracker
import com.fitnessapp.cv.engine.WorkoutSnapshot
import com.fitnessapp.cv.metrics.FpsMeter
import com.fitnessapp.cv.pose.MediaPipePoseEngine
import com.fitnessapp.cv.pose.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkoutUiState(
    val frame: PoseFrame? = null,
    val snapshot: WorkoutSnapshot? = null,
    val cameraFps: Float = 0f,
    val poseFps: Float = 0f,
    val inferenceMs: Long = 0,
    val error: String? = null,
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    private val tracker = BicepCurlTracker()
    private val cameraFps = FpsMeter()
    private val poseFps = FpsMeter()

    private val engine: MediaPipePoseEngine? = try {
        MediaPipePoseEngine(application).also { it.setListener(::onPoseResult) }
    } catch (e: Exception) {
        _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        null
    }

    /** Called on the camera analysis thread for every frame. */
    fun onCameraFrame(image: ImageProxy) {
        cameraFps.tick(System.currentTimeMillis())
        if (engine == null) {
            image.close()
            return
        }
        engine.submit(image)
    }

    /** Clears the counter and tracking state, e.g. to start a new set. */
    fun reset() {
        synchronized(tracker) { tracker.reset() }
    }

    private fun onPoseResult(frame: PoseFrame) {
        poseFps.tick(System.currentTimeMillis())
        // Results arrive on a MediaPipe thread; the tracker is not thread-safe, so serialize access.
        val snapshot = synchronized(tracker) { tracker.process(frame) }
        _state.update {
            it.copy(
                frame = frame,
                snapshot = snapshot,
                cameraFps = cameraFps.fps,
                poseFps = poseFps.fps,
                inferenceMs = frame.inferenceMs,
            )
        }
    }

    override fun onCleared() {
        engine?.close()
    }
}
