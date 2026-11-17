package com.fitnessapp.cv.engine

/**
 * Totals of one finished set, shown on the summary screen.
 *
 * [durationMs] runs from the moment counting first started to the last processed frame, and is
 * split into [outOfFrameMs] (the person was out of frame) and [activeMs] (everything else).
 */
data class SessionSummary(
    val reps: Int,
    val durationMs: Long,
    val activeMs: Long,
    val outOfFrameMs: Long,
    val outOfFrameCount: Int,
    /** Mean duration of one repetition, null when none was counted. */
    val averageRepMs: Long?,
    /** Smallest and largest smoothed elbow angle seen while counting. */
    val minAngleDeg: Double?,
    val maxAngleDeg: Double?,
    /** Mean pose results per second over the whole session. */
    val averageFps: Float,
) {
    /** Counted minus the number the user counted by hand: positive means the app counted more. */
    fun differenceTo(manualCount: Int): Int = reps - manualCount
}
