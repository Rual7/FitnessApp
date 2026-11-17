package com.fitnessapp.cv.engine

/**
 * Bicep curl counting parameters. All values are placeholder starting points, not validated yet:
 * they are hardcoded because this exercise only exists for the prototype.
 *
 * The gap between the two thresholds is the hysteresis: jitter around a threshold cannot
 * produce false repetitions.
 */
data class CurlConfig(
    /** Elbow angle above which the arm counts as extended. */
    val extendedAboveDeg: Double = 150.0,
    /** Elbow angle below which the arm counts as flexed. */
    val flexedBelowDeg: Double = 60.0,
    /** Consecutive frames beyond a threshold before the phase changes. */
    val minFramesInPhase: Int = 2,
    /** Smoothing factor of the exponential moving average (1 = no smoothing). */
    val smoothingAlpha: Double = 0.5,
)

enum class CurlPhase { UNKNOWN, EXTENDED, FLEXED }

/**
 * Repetition state machine: extended -> flexed -> extended counts one repetition.
 *
 * A repetition is only counted when it started from the extended phase, so a rep that is
 * cancelled (person left the frame) and a "flexed" phase seen first after a restart are never counted.
 */
class RepCounter(private val config: CurlConfig = CurlConfig()) {

    var reps = 0
        private set
    var phase = CurlPhase.UNKNOWN
        private set
    var minAngleDeg: Double? = null
        private set
    var maxAngleDeg: Double? = null
        private set

    /** True when the current flexed phase started from a confirmed extended phase. */
    private var armed = false
    private var candidate = CurlPhase.UNKNOWN
    private var candidateFrames = 0

    /** Feeds one (smoothed) elbow angle. Returns true when this frame completed a repetition. */
    fun update(angleDeg: Double): Boolean {
        minAngleDeg = minAngleDeg?.let { minOf(it, angleDeg) } ?: angleDeg
        maxAngleDeg = maxAngleDeg?.let { maxOf(it, angleDeg) } ?: angleDeg

        val observed = when {
            angleDeg >= config.extendedAboveDeg -> CurlPhase.EXTENDED
            angleDeg <= config.flexedBelowDeg -> CurlPhase.FLEXED
            else -> {
                // Inside the hysteresis band: the phase does not change, and the streak of
                // consecutive frames beyond a threshold is broken.
                candidate = CurlPhase.UNKNOWN
                candidateFrames = 0
                return false
            }
        }
        if (observed == phase) {
            candidate = CurlPhase.UNKNOWN
            candidateFrames = 0
            return false
        }
        if (observed != candidate) {
            candidate = observed
            candidateFrames = 0
        }
        candidateFrames++
        if (candidateFrames < config.minFramesInPhase) return false

        candidate = CurlPhase.UNKNOWN
        candidateFrames = 0
        return when (observed) {
            CurlPhase.EXTENDED -> {
                val completed = phase == CurlPhase.FLEXED && armed
                if (completed) reps++
                phase = CurlPhase.EXTENDED
                armed = true
                completed
            }
            CurlPhase.FLEXED -> {
                // armed stays true only if the previous phase was a real extended phase
                armed = phase == CurlPhase.EXTENDED
                phase = CurlPhase.FLEXED
                false
            }
            CurlPhase.UNKNOWN -> false
        }
    }

    /**
     * Drops the repetition in progress (tracking was interrupted). The counter restarts from the
     * phase the person is in when tracking resumes, not from the one before the interruption.
     */
    fun cancelRepetition() {
        phase = CurlPhase.UNKNOWN
        armed = false
        candidate = CurlPhase.UNKNOWN
        candidateFrames = 0
    }

    fun reset() {
        cancelRepetition()
        reps = 0
        minAngleDeg = null
        maxAngleDeg = null
    }
}

/** Exponential moving average used to calm landmark jitter on the angle. */
class ExponentialSmoother(private val alpha: Double) {
    private var value: Double? = null

    fun add(x: Double): Double {
        val v = value?.let { alpha * x + (1 - alpha) * it } ?: x
        value = v
        return v
    }

    fun reset() {
        value = null
    }
}
