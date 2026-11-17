package com.fitnessapp.cv

import com.fitnessapp.cv.engine.CurlPhase
import com.fitnessapp.cv.engine.RepCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class RepCounterTest {

    /** Feeds each angle for three frames, enough to pass the minimum-frames filter. */
    private fun RepCounter.feed(vararg angles: Double) {
        for (a in angles) repeat(3) { update(a) }
    }

    @Test
    fun fullRepetitionIsCounted() {
        val c = RepCounter()
        c.feed(170.0, 100.0, 40.0, 100.0, 170.0)
        assertEquals(1, c.reps)
        assertEquals(CurlPhase.EXTENDED, c.phase)
    }

    @Test
    fun threeRepetitionsAreCounted() {
        val c = RepCounter()
        c.feed(170.0)
        repeat(3) { c.feed(100.0, 40.0, 100.0, 170.0) }
        assertEquals(3, c.reps)
    }

    @Test
    fun incompleteRepetitionIsNotCounted() {
        val c = RepCounter()
        // never goes below the flexed threshold (60)
        c.feed(170.0, 100.0, 80.0, 100.0, 170.0)
        assertEquals(0, c.reps)
    }

    @Test
    fun notFullyExtendedAfterwardsIsNotCounted() {
        val c = RepCounter()
        c.feed(170.0, 40.0, 120.0, 40.0, 120.0)
        assertEquals(0, c.reps)
    }

    @Test
    fun jitterAroundThresholdDoesNotCreateRepetitions() {
        val c = RepCounter()
        c.feed(170.0, 40.0)
        // wobble inside the hysteresis band and just around the extended threshold, never settling
        repeat(10) {
            c.update(152.0); c.update(148.0)
        }
        assertEquals(0, c.reps)
    }

    @Test
    fun singleFrameSpikeIsIgnored() {
        val c = RepCounter()
        c.feed(170.0, 40.0)
        c.update(170.0) // one frame only: below minFramesInPhase
        c.update(40.0)
        assertEquals(0, c.reps)
        assertEquals(CurlPhase.FLEXED, c.phase)
    }

    @Test
    fun cancelledRepetitionIsNeverCounted() {
        val c = RepCounter()
        c.feed(170.0, 40.0)
        c.cancelRepetition() // person left the frame mid-repetition
        c.feed(170.0) // comes back with the arm extended
        assertEquals(0, c.reps)
        c.feed(40.0, 170.0)
        assertEquals(1, c.reps)
    }

    @Test
    fun startingFlexedDoesNotCountUntilAnExtendedPhaseWasSeen() {
        val c = RepCounter()
        c.feed(40.0, 170.0) // first phase seen is flexed: this is not a full repetition
        assertEquals(0, c.reps)
        c.feed(40.0, 170.0)
        assertEquals(1, c.reps)
    }

    @Test
    fun amplitudeIsTracked() {
        val c = RepCounter()
        c.feed(170.0, 45.0, 165.0)
        assertEquals(45.0, c.minAngleDeg!!, 0.001)
        assertEquals(170.0, c.maxAngleDeg!!, 0.001)
    }
}
