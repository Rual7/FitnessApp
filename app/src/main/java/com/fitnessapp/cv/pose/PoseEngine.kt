package com.fitnessapp.cv.pose

/** Receives one [PoseFrame] per processed camera frame. May be called from a non-UI thread. */
fun interface PoseResultListener {
    fun onResult(frame: PoseFrame)
}

/**
 * Source of pose results. The live implementation wraps MediaPipe ([MediaPipePoseEngine]);
 * a replay implementation can feed recorded landmark sequences into the same pipeline.
 */
interface PoseEngine : AutoCloseable {
    fun setListener(listener: PoseResultListener?)
}
