package com.fitnessapp.cv

import com.fitnessapp.cv.engine.CurlPhase
import com.fitnessapp.cv.engine.RepCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RepCounterTest {

    private var clock = 0L

    /** Feeds one angle as the next frame (33 ms after the previous one). */
    private fun RepCounter.step(angle: Double): Boolean = update(angle, clock).also { clock += 33 }

    /** Feeds each angle for three frames, enough to pass the minimum-frames filter. */
    private fun RepCounter.feed(vararg angles: Double) {
        for (a in angles) repeat(3) { step(a) }
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
            c.step(152.0); c.step(148.0)
        }
        assertEquals(0, c.reps)
    }

    @Test
    fun singleFrameSpikeIsIgnored() {
        val c = RepCounter()
        c.feed(170.0, 40.0)
        c.step(170.0) // one frame only: below minFramesInPhase
        c.step(40.0)
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

    @Test
    fun repetitionLastsFromLeavingExtendedToReturningToIt() {
        val c = RepCounter()
        c.update(170.0, 0)
        c.update(170.0, 33) // extended is confirmed
        c.update(120.0, 66) // the arm leaves the extended zone: the repetition starts here
        c.update(40.0, 99)
        c.update(40.0, 132) // flexed is confirmed
        c.update(100.0, 165)
        c.update(170.0, 198)
        c.update(170.0, 231) // extended is confirmed again: the repetition is counted
        assertEquals(1, c.reps)
        assertEquals(165L, c.averageRepMs)
    }

    @Test
    fun noAverageBeforeTheFirstRepetition() {
        val c = RepCounter()
        c.feed(170.0, 100.0)
        assertNull(c.averageRepMs)
    }

    @Test
    fun cancelledRepetitionIsNotTimedAndResetClearsTheAverage() {
        val c = RepCounter()
        c.feed(170.0, 40.0)
        c.cancelRepetition()
        c.feed(40.0, 170.0) // comes back flexed: not a repetition, so nothing is timed
        assertEquals(0, c.reps)
        assertNull(c.averageRepMs)

        c.feed(40.0, 170.0) // a real repetition
        assertEquals(1, c.reps)
        assertNotNull(c.averageRepMs)
        c.reset()
        assertNull(c.averageRepMs)
    }
}
