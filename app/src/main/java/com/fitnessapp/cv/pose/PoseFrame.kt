package com.fitnessapp.cv.pose

/**
 * One body landmark. x and y are normalized to the (upright) image, 0..1, so they may fall outside
 * that range when the body part is outside the image. z is relative depth (noisy).
 * [visibility] and [presence] are the 0..1 scores reported by the model.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float,
)

/**
 * The pose result for one camera frame. Pure data, no Android types, so the counting engine
 * can run on the JVM and be fed from recorded fixtures.
 *
 * [landmarks] is null when no person was detected, otherwise the 33 BlazePose landmarks.
 */
data class PoseFrame(
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val landmarks: List<Landmark>?,
    val inferenceMs: Long = 0,
)

/** Indices of the 33 BlazePose landmarks used by the app. */
object PoseLandmarks {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val COUNT = 33

    /** Landmarks 0..10 are the face; the app only draws and uses the body (11..32). */
    const val FIRST_BODY_INDEX = 11

    /** Body skeleton edges for drawing (no face). */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        // torso
        11 to 12, 11 to 23, 12 to 24, 23 to 24,
        // left arm and hand
        11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
        // right arm and hand
        12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
        // left leg and foot
        23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31,
        // right leg and foot
        24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32,
    )
}
