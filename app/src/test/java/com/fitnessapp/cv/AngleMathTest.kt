package com.fitnessapp.cv

import com.fitnessapp.cv.metrics.AngleMath
import com.fitnessapp.cv.pose.Landmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleMathTest {
    private fun p(x: Float, y: Float) = Landmark(x, y, 0f, 1f, 1f)

    @Test
    fun rightAngleIs90() {
        val angle = AngleMath.angleDegrees(p(0.5f, 0.3f), p(0.5f, 0.5f), p(0.7f, 0.5f), 1000, 1000)
        assertEquals(90.0, angle, 0.01)
    }

    @Test
    fun straightLineIs180() {
        val angle = AngleMath.angleDegrees(p(0.5f, 0.3f), p(0.5f, 0.5f), p(0.5f, 0.7f), 1000, 1000)
        assertEquals(180.0, angle, 0.01)
    }

    @Test
    fun foldedBackIs0() {
        val angle = AngleMath.angleDegrees(p(0.5f, 0.3f), p(0.5f, 0.5f), p(0.5f, 0.3f), 1000, 1000)
        assertEquals(0.0, angle, 0.01)
    }

    @Test
    fun imageAspectRatioIsAccountedFor() {
        // Normalized offsets (0.1, 0) and (0.1, 0.1) on a 2000x1000 image are (200, 0) and (200, 100) pixels: ~26.57 degrees.
        val angle = AngleMath.angleDegrees(p(0.6f, 0.5f), p(0.5f, 0.5f), p(0.6f, 0.6f), 2000, 1000)
        assertEquals(26.565, angle, 0.01)
    }

    @Test
    fun coincidentPointsGiveNaN() {
        val angle = AngleMath.angleDegrees(p(0.5f, 0.5f), p(0.5f, 0.5f), p(0.7f, 0.5f), 1000, 1000)
        assertTrue(angle.isNaN())
    }
}
