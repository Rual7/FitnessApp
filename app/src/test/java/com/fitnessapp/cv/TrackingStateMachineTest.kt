package com.fitnessapp.cv

import com.fitnessapp.cv.tracking.TrackingConfig
import com.fitnessapp.cv.tracking.TrackingState
import com.fitnessapp.cv.tracking.TrackingStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStateMachineTest {
    private val cfg = TrackingConfig(readyFrames = 3, countdownMs = 1000, graceMs = 500, returnStableFrames = 3, longAbsenceMs = 5000)
    private val frameMs = 100L

    private class Clock { var now = 0L }

    private fun TrackingStateMachine.step(clock: Clock, person: Boolean, usable: Boolean, n: Int = 1) =
        (1..n).map { clock.now += frameMs; update(clock.now, person, usable) }.last()

    private fun TrackingStateMachine.reachCounting(clock: Clock) {
        step(clock, person = true, usable = true, n = 3) // POSITIONING -> READY
        step(clock, person = true, usable = true, n = 11) // countdown
        assertEquals(TrackingState.COUNTING, state)
    }

    @Test
    fun startsSearching() {
        assertEquals(TrackingState.SEARCHING, TrackingStateMachine(cfg).state)
    }

    @Test
    fun personWithoutUsableArmsIsPositioning() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.step(clock, person = true, usable = false, n = 5)
        assertEquals(TrackingState.POSITIONING, sm.state)
    }

    @Test
    fun stableUsableFramesLeadToReadyThenCounting() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.step(clock, person = true, usable = true, n = 3)
        assertEquals(TrackingState.READY, sm.state)
        assertTrue(sm.countdownRemainingMs(clock.now)!! > 0)
        sm.step(clock, person = true, usable = true, n = 11)
        assertEquals(TrackingState.COUNTING, sm.state)
    }

    @Test
    fun losingTheArmsDuringCountdownGoesBackToPositioning() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.step(clock, person = true, usable = true, n = 3)
        sm.step(clock, person = true, usable = false)
        assertEquals(TrackingState.POSITIONING, sm.state)
    }

    @Test
    fun shortOcclusionStaysInCountingWithoutInterruption() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.reachCounting(clock)
        val update = sm.step(clock, person = true, usable = false, n = 3) // 300 ms < 500 ms grace
        assertEquals(TrackingState.COUNTING, update.state)
        assertFalse(update.interrupted)
        assertEquals(0, sm.outOfFrameCount)
    }

    @Test
    fun longAbsenceInterruptsAndIsTimed() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.reachCounting(clock)
        val lostAt = clock.now + frameMs
        var interruptedSeen = false
        repeat(10) {
            clock.now += frameMs
            if (sm.update(clock.now, personDetected = false, usable = false).interrupted) interruptedSeen = true
        }
        assertTrue(interruptedSeen)
        assertEquals(TrackingState.OUT_OF_FRAME, sm.state)
        assertEquals(1, sm.outOfFrameCount)
        // absence is measured from the moment tracking was lost
        assertEquals(clock.now - lostAt, sm.currentAbsenceMs(clock.now))
    }

    @Test
    fun returnNeedsStableFramesAndThenResumes() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.reachCounting(clock)
        sm.step(clock, person = false, usable = false, n = 10)
        assertEquals(TrackingState.OUT_OF_FRAME, sm.state)

        sm.step(clock, person = true, usable = true, n = 2) // not enough yet
        assertEquals(TrackingState.OUT_OF_FRAME, sm.state)
        sm.step(clock, person = true, usable = false) // flicker resets the stability count
        sm.step(clock, person = true, usable = true, n = 2)
        assertEquals(TrackingState.OUT_OF_FRAME, sm.state)

        val update = sm.step(clock, person = true, usable = true)
        assertEquals(TrackingState.COUNTING, update.state)
        assertTrue(update.resumed)
        assertTrue(sm.outOfFrameMs(clock.now) > 0)
    }

    @Test
    fun veryLongAbsenceSuggestsEnding() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.reachCounting(clock)
        sm.step(clock, person = false, usable = false, n = 10)
        assertFalse(sm.suggestEnd(clock.now))
        sm.step(clock, person = false, usable = false, n = 60)
        assertTrue(sm.suggestEnd(clock.now))
    }

    @Test
    fun totalOutOfFrameTimeAccumulatesAcrossExits() {
        val sm = TrackingStateMachine(cfg)
        val clock = Clock()
        sm.reachCounting(clock)
        repeat(2) {
            sm.step(clock, person = false, usable = false, n = 10)
            sm.step(clock, person = true, usable = true, n = 3)
        }
        assertEquals(2, sm.outOfFrameCount)
        assertTrue(sm.outOfFrameMs(clock.now) >= 2 * 900)
    }
}
