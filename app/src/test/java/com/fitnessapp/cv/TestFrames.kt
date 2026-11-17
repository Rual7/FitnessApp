package com.fitnessapp.cv

import com.fitnessapp.cv.pose.Landmark
import com.fitnessapp.cv.pose.PoseFrame
import com.fitnessapp.cv.pose.PoseLandmarks
import kotlin.math.cos
import kotlin.math.sin

/** Builds synthetic pose frames with a chosen elbow angle, on a square image. */
object TestFrames {
    private const val SIZE = 1000
    private val HIDDEN = Landmark(0.5f, 0.5f, 0f, visibility = 0f, presence = 0f)

    private fun visible(x: Float, y: Float) = Landmark(x, y, 0f, visibility = 0.95f, presence = 0.95f)

    /**
     * @param elbowAngleDeg angle at the elbow for both arms; null = no person detected
     * @param shiftY moves the whole arm vertically; a large value pushes the wrist outside the image
     */
    fun frame(timestampMs: Long, elbowAngleDeg: Double?, shiftY: Float = 0f): PoseFrame {
        if (elbowAngleDeg == null) {
            return PoseFrame(timestampMs, SIZE, SIZE, landmarks = null)
        }
        val lm = MutableList(PoseLandmarks.COUNT) { HIDDEN }
        placeArm(lm, 0.3f, PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST, elbowAngleDeg, shiftY)
        placeArm(lm, 0.7f, PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST, elbowAngleDeg, shiftY)
        return PoseFrame(timestampMs, SIZE, SIZE, lm)
    }

    /** Only the shoulders visible (arms hidden): a person is detected but cannot be tracked. */
    fun personWithoutArms(timestampMs: Long): PoseFrame {
        val lm = MutableList(PoseLandmarks.COUNT) { HIDDEN }
        lm[PoseLandmarks.LEFT_SHOULDER] = visible(0.3f, 0.3f)
        lm[PoseLandmarks.RIGHT_SHOULDER] = visible(0.7f, 0.3f)
        return PoseFrame(timestampMs, SIZE, SIZE, lm)
    }

    private fun placeArm(lm: MutableList<Landmark>, x: Float, shoulder: Int, elbow: Int, wrist: Int, angleDeg: Double, shiftY: Float) {
        val sy = 0.3f + shiftY
        val ey = 0.5f + shiftY
        val rad = Math.toRadians(angleDeg)
        // The shoulder is straight above the elbow; the wrist direction is rotated by the angle from "up".
        val wx = x + (0.2 * sin(rad)).toFloat()
        val wy = ey + (-0.2 * cos(rad)).toFloat()
        lm[shoulder] = visible(x, sy)
        lm[elbow] = visible(x, ey)
        lm[wrist] = visible(wx, wy)
    }
}
