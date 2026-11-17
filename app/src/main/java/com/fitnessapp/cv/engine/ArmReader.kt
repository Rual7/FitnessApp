package com.fitnessapp.cv.engine

import com.fitnessapp.cv.metrics.AngleMath
import com.fitnessapp.cv.pose.Landmark
import com.fitnessapp.cv.pose.PoseFrame
import com.fitnessapp.cv.pose.PoseLandmarks

/** What to tell the user when the arms are not usable. */
enum class PositioningHint { NONE, SHOW_ARM, STEP_BACK }

data class ArmReading(
    val personDetected: Boolean,
    /** True when at least one arm has all three landmarks visible and inside the image. */
    val usable: Boolean,
    /** Elbow angle in degrees (average of both arms when both are usable), null when not usable. */
    val elbowAngleDeg: Double?,
    val hint: PositioningHint,
)

/**
 * Reads the elbow angle (shoulder-elbow-wrist) from a pose frame.
 *
 * Only usable arms are read; when both are usable the result is the average of both (simultaneous curls).
 * "Usable" means a visibility score >= [minVisibility] for all three landmarks and the landmarks
 * lying inside the image. The default 0.5 is a placeholder to tune on real recordings, and the
 * presence score is not used yet.
 */
class ArmReader(private val minVisibility: Float = 0.5f) {

    private class Arm(val shoulder: Int, val elbow: Int, val wrist: Int)

    private val left = Arm(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST)
    private val right = Arm(PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST)

    fun read(frame: PoseFrame): ArmReading {
        val lm = frame.landmarks
            ?: return ArmReading(personDetected = false, usable = false, elbowAngleDeg = null, hint = PositioningHint.NONE)
        if (lm.size < PoseLandmarks.COUNT) {
            return ArmReading(false, false, null, PositioningHint.NONE)
        }

        val angles = listOf(left, right).mapNotNull { arm ->
            if (isUsable(lm, arm)) {
                AngleMath.angleDegrees(lm[arm.shoulder], lm[arm.elbow], lm[arm.wrist], frame.imageWidth, frame.imageHeight)
                    .takeUnless { it.isNaN() }
            } else {
                null
            }
        }
        if (angles.isNotEmpty()) {
            return ArmReading(true, true, angles.average(), PositioningHint.NONE)
        }

        val anyOutside = listOf(left, right).any { arm ->
            listOf(arm.shoulder, arm.elbow, arm.wrist).any { !insideImage(lm[it]) }
        }
        return ArmReading(
            personDetected = true,
            usable = false,
            elbowAngleDeg = null,
            hint = if (anyOutside) PositioningHint.STEP_BACK else PositioningHint.SHOW_ARM,
        )
    }

    private fun isUsable(lm: List<Landmark>, arm: Arm): Boolean =
        listOf(arm.shoulder, arm.elbow, arm.wrist).all { lm[it].visibility >= minVisibility && insideImage(lm[it]) }

    private fun insideImage(l: Landmark) = l.x in 0f..1f && l.y in 0f..1f
}
