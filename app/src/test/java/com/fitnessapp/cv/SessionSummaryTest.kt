package com.fitnessapp.cv

import com.fitnessapp.cv.engine.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryTest {
    private fun summary(reps: Int) = SessionSummary(
        reps = reps,
        durationMs = 0,
        activeMs = 0,
        outOfFrameMs = 0,
        outOfFrameCount = 0,
        averageRepMs = null,
        minAngleDeg = null,
        maxAngleDeg = null,
        averageFps = 0f,
    )

    @Test
    fun sameCountGivesZero() = assertEquals(0, summary(10).differenceTo(10))

    @Test
    fun appCountingMoreIsPositive() = assertEquals(2, summary(12).differenceTo(10))

    @Test
    fun appCountingFewerIsNegative() = assertEquals(-3, summary(7).differenceTo(10))
}
