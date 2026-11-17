package com.fitnessapp.cv

import com.fitnessapp.cv.metrics.FpsMeter
import org.junit.Assert.assertEquals
import org.junit.Test

class FpsMeterTest {
    @Test
    fun measuresThirtyFps() {
        val m = FpsMeter()
        var t = 0L
        repeat(100) { m.tick(t); t += 33 }
        assertEquals(30f, m.fps, 1.5f)
        assertEquals(30f, m.averageFps, 1.5f)
    }

    @Test
    fun zeroBeforeFirstWindow() {
        val m = FpsMeter()
        m.tick(0)
        m.tick(100)
        assertEquals(0f, m.fps, 0.001f)
    }
}
