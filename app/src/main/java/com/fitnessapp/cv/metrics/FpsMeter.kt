package com.fitnessapp.cv.metrics

/**
 * Frames-per-second meter over a sliding window. Pure Kotlin (no Android types) so it can be unit tested.
 * Not thread-safe: call [tick] from a single thread.
 */
class FpsMeter(private val windowMs: Long = 1000L) {
    private var windowStart = -1L
    private var frames = 0

    /** Latest completed-window FPS, 0 until the first window has elapsed. */
    var fps: Float = 0f
        private set

    private var totalFrames = 0L
    private var firstTick = -1L
    private var lastTick = -1L

    /** Average FPS since the first tick (used for the session summary later). */
    val averageFps: Float
        get() = if (totalFrames < 2 || lastTick <= firstTick) 0f
        else (totalFrames - 1) * 1000f / (lastTick - firstTick)

    fun tick(nowMs: Long) {
        if (firstTick < 0) firstTick = nowMs
        lastTick = nowMs
        totalFrames++
        if (windowStart < 0) windowStart = nowMs
        frames++
        val elapsed = nowMs - windowStart
        if (elapsed >= windowMs) {
            fps = frames * 1000f / elapsed
            frames = 0
            windowStart = nowMs
        }
    }
}
