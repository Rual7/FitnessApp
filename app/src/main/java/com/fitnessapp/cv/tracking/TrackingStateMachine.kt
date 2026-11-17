package com.fitnessapp.cv.tracking

enum class TrackingState { SEARCHING, POSITIONING, READY, COUNTING, OUT_OF_FRAME }

/**
 * All durations and frame counts are PLACEHOLDER starting values (docs/PROTOTYPE.md section 5):
 * they are tuned experimentally on the test phone.
 */
data class TrackingConfig(
    /** Consecutive usable frames needed before the countdown starts. placeholder */
    val readyFrames: Int = 10,
    /** Countdown before counting begins. placeholder */
    val countdownMs: Long = 3000,
    /** A brief occlusion shorter than this does not interrupt counting. placeholder */
    val graceMs: Long = 800,
    /** Consecutive usable frames needed to resume after leaving the frame. placeholder */
    val returnStableFrames: Int = 5,
    /** After this long out of frame, the app suggests ending the session. placeholder */
    val longAbsenceMs: Long = 10_000,
)

/** What changed on the latest update. */
data class TrackingUpdate(
    val state: TrackingState,
    /** The person just left the frame: the repetition in progress must be dropped. */
    val interrupted: Boolean,
    /** The person just came back and counting resumes. */
    val resumed: Boolean,
)

/**
 * Decides whether the person can be tracked, and handles leaving and re-entering the frame.
 * Time comes from frame timestamps, so it is deterministic and testable on the JVM.
 *
 *     SEARCHING -> POSITIONING -> READY -> COUNTING <-> OUT_OF_FRAME
 */
class TrackingStateMachine(private val config: TrackingConfig = TrackingConfig()) {

    var state = TrackingState.SEARCHING
        private set

    /** Number of times the person left the frame while counting. */
    var outOfFrameCount = 0
        private set

    /** Total completed time out of frame. Use [outOfFrameMs] to include the current absence. */
    private var completedOutOfFrameMs = 0L
    private var stableFrames = 0
    private var readyAtMs = 0L
    private var unusableSinceMs: Long? = null
    private var outOfFrameSinceMs = 0L

    /** Time left of the countdown while READY, else null. */
    fun countdownRemainingMs(nowMs: Long): Long? =
        if (state == TrackingState.READY) (config.countdownMs - (nowMs - readyAtMs)).coerceAtLeast(0) else null

    /** Total time out of frame, including an absence still in progress. */
    fun outOfFrameMs(nowMs: Long): Long =
        completedOutOfFrameMs + if (state == TrackingState.OUT_OF_FRAME) nowMs - outOfFrameSinceMs else 0

    /** Length of the current absence, 0 when the person is not out of frame. */
    fun currentAbsenceMs(nowMs: Long): Long =
        if (state == TrackingState.OUT_OF_FRAME) nowMs - outOfFrameSinceMs else 0

    fun suggestEnd(nowMs: Long): Boolean = currentAbsenceMs(nowMs) >= config.longAbsenceMs

    fun update(nowMs: Long, personDetected: Boolean, usable: Boolean): TrackingUpdate {
        var interrupted = false
        var resumed = false

        when (state) {
            TrackingState.SEARCHING -> {
                if (personDetected) {
                    state = TrackingState.POSITIONING
                    stableFrames = 0
                    positioningStep(nowMs, usable)
                }
            }

            TrackingState.POSITIONING -> {
                if (!personDetected) {
                    state = TrackingState.SEARCHING
                    stableFrames = 0
                } else {
                    positioningStep(nowMs, usable)
                }
            }

            TrackingState.READY -> {
                when {
                    !personDetected -> {
                        state = TrackingState.SEARCHING
                        stableFrames = 0
                    }
                    !usable -> {
                        state = TrackingState.POSITIONING
                        stableFrames = 0
                    }
                    nowMs - readyAtMs >= config.countdownMs -> {
                        state = TrackingState.COUNTING
                        unusableSinceMs = null
                    }
                }
            }

            TrackingState.COUNTING -> {
                if (usable) {
                    unusableSinceMs = null
                } else {
                    val since = unusableSinceMs ?: nowMs.also { unusableSinceMs = it }
                    if (nowMs - since >= config.graceMs) {
                        state = TrackingState.OUT_OF_FRAME
                        outOfFrameSinceMs = since // the absence is counted from when tracking was lost
                        outOfFrameCount++
                        stableFrames = 0
                        unusableSinceMs = null
                        interrupted = true
                    }
                }
            }

            TrackingState.OUT_OF_FRAME -> {
                if (usable) {
                    stableFrames++
                    if (stableFrames >= config.returnStableFrames) {
                        completedOutOfFrameMs += nowMs - outOfFrameSinceMs
                        state = TrackingState.COUNTING
                        stableFrames = 0
                        resumed = true
                    }
                } else {
                    stableFrames = 0
                }
            }
        }
        return TrackingUpdate(state, interrupted, resumed)
    }

    private fun positioningStep(nowMs: Long, usable: Boolean) {
        if (!usable) {
            stableFrames = 0
            return
        }
        stableFrames++
        if (stableFrames >= config.readyFrames) {
            state = TrackingState.READY
            readyAtMs = nowMs
            stableFrames = 0
        }
    }

    fun reset() {
        state = TrackingState.SEARCHING
        outOfFrameCount = 0
        completedOutOfFrameMs = 0
        stableFrames = 0
        unusableSinceMs = null
    }
}
