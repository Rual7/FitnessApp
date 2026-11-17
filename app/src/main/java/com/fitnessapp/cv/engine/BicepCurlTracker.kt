package com.fitnessapp.cv.engine

import com.fitnessapp.cv.pose.PoseFrame
import com.fitnessapp.cv.tracking.TrackingConfig
import com.fitnessapp.cv.tracking.TrackingState
import com.fitnessapp.cv.tracking.TrackingStateMachine

/** Which message the UI should show. Mapped to string resources in the UI layer. */
enum class TrackingMessage {
    ENTER_FRAME,
    SHOW_ARM,
    STEP_BACK,
    GET_READY,
    COUNTING,
    OUT_OF_FRAME,
    SUGGEST_END,
}

data class WorkoutSnapshot(
    val tracking: TrackingState,
    val message: TrackingMessage,
    val reps: Int,
    val phase: CurlPhase,
    /** Smoothed elbow angle of the latest frame, null when not measurable. */
    val elbowAngleDeg: Double?,
    val minAngleDeg: Double?,
    val maxAngleDeg: Double?,
    val countdownRemainingMs: Long?,
    val outOfFrameMs: Long,
    val currentAbsenceMs: Long,
    val outOfFrameCount: Int,
)

/**
 * Pose frames in, tracking state + repetition count out. Pure Kotlin (no Android), so it runs
 * on the JVM against synthetic or recorded landmark sequences.
 * Not thread-safe: feed it from one thread.
 */
class BicepCurlTracker(
    curl: CurlConfig = CurlConfig(),
    tracking: TrackingConfig = TrackingConfig(),
) {
    private val armReader = ArmReader()
    private val stateMachine = TrackingStateMachine(tracking)
    private val counter = RepCounter(curl)
    private val smoother = ExponentialSmoother(curl.smoothingAlpha)

    fun process(frame: PoseFrame): WorkoutSnapshot {
        val now = frame.timestampMs
        val reading = armReader.read(frame)
        val update = stateMachine.update(now, reading.personDetected, reading.usable)

        if (update.interrupted || update.resumed) {
            // An interrupted repetition is never counted; after resuming, the machine restarts
            // from the phase the person is actually in.
            counter.cancelRepetition()
            smoother.reset()
        }

        var smoothed: Double? = null
        if (update.state == TrackingState.COUNTING && reading.elbowAngleDeg != null) {
            smoothed = smoother.add(reading.elbowAngleDeg)
            counter.update(smoothed)
        }

        return WorkoutSnapshot(
            tracking = update.state,
            message = messageFor(update.state, reading.hint, now),
            reps = counter.reps,
            phase = counter.phase,
            elbowAngleDeg = smoothed ?: reading.elbowAngleDeg,
            minAngleDeg = counter.minAngleDeg,
            maxAngleDeg = counter.maxAngleDeg,
            countdownRemainingMs = stateMachine.countdownRemainingMs(now),
            outOfFrameMs = stateMachine.outOfFrameMs(now),
            currentAbsenceMs = stateMachine.currentAbsenceMs(now),
            outOfFrameCount = stateMachine.outOfFrameCount,
        )
    }

    private fun messageFor(state: TrackingState, hint: PositioningHint, now: Long): TrackingMessage = when (state) {
        TrackingState.SEARCHING -> TrackingMessage.ENTER_FRAME
        TrackingState.POSITIONING ->
            if (hint == PositioningHint.STEP_BACK) TrackingMessage.STEP_BACK else TrackingMessage.SHOW_ARM
        TrackingState.READY -> TrackingMessage.GET_READY
        TrackingState.COUNTING -> TrackingMessage.COUNTING
        TrackingState.OUT_OF_FRAME ->
            if (stateMachine.suggestEnd(now)) TrackingMessage.SUGGEST_END else TrackingMessage.OUT_OF_FRAME
    }

    fun reset() {
        stateMachine.reset()
        counter.reset()
        smoother.reset()
    }
}
