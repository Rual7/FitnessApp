package com.fitnessapp.cv

import com.fitnessapp.cv.engine.BicepCurlTracker
import com.fitnessapp.cv.engine.TrackingMessage
import com.fitnessapp.cv.engine.WorkoutSnapshot
import com.fitnessapp.cv.tracking.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** End-to-end tests of the counting pipeline on synthetic landmark sequences (no camera). */
class BicepCurlTrackerTest {

    private class Sim {
        val tracker = BicepCurlTracker()
        var now = 0L
        var last: WorkoutSnapshot? = null

        fun hold(angle: Double?, frames: Int, shiftY: Float = 0f) {
            repeat(frames) {
                now += 33
                last = tracker.process(TestFrames.frame(now, angle, shiftY))
            }
        }

        /** One smooth repetition: extended -> flexed -> extended over about two seconds. */
        fun rep(bottom: Double = 40.0, steps: Int = 30) {
            for (i in 0..steps) {
                val t = i / steps.toDouble()
                val angle = 170.0 - (170.0 - bottom) * (1 - Math.abs(2 * t - 1))
                hold(angle, 1)
            }
        }

        /** A repetition that goes down to [bottom] and stops there. */
        fun halfRep(bottom: Double = 40.0, steps: Int = 15) {
            for (i in 0..steps) hold(170.0 - (170.0 - bottom) * (i / steps.toDouble()), 1)
        }

        fun reachCounting() {
            hold(170.0, 130) // 10 stable frames, then the 3 s countdown (130 frames of 33 ms)
            assertEquals(TrackingState.COUNTING, last!!.tracking)
        }
    }

    @Test
    fun countsRepetitions() {
        val s = Sim()
        s.reachCounting()
        repeat(5) { s.rep() }
        assertEquals(5, s.last!!.reps)
    }

    @Test
    fun partialRepetitionsAreNotCounted() {
        val s = Sim()
        s.reachCounting()
        s.rep()
        s.rep(bottom = 90.0) // does not reach the flexed threshold
        s.rep(bottom = 90.0)
        s.rep()
        assertEquals(2, s.last!!.reps)
    }

    @Test
    fun nothingIsCountedBeforeCountdownEnds() {
        val s = Sim()
        s.hold(170.0, 20)
        assertEquals(TrackingState.READY, s.last!!.tracking)
        assertEquals(TrackingMessage.GET_READY, s.last!!.message)
        s.rep()
        assertEquals(0, s.last!!.reps)
    }

    @Test
    fun noPersonMeansSearching() {
        val s = Sim()
        s.hold(null, 10)
        assertEquals(TrackingState.SEARCHING, s.last!!.tracking)
        assertEquals(TrackingMessage.ENTER_FRAME, s.last!!.message)
    }

    @Test
    fun armsOutOfImageAskToStepBack() {
        val s = Sim()
        s.hold(170.0, 10, shiftY = 0.6f) // arms pushed below the bottom edge
        assertEquals(TrackingState.POSITIONING, s.last!!.tracking)
        assertEquals(TrackingMessage.STEP_BACK, s.last!!.message)
    }

    @Test
    fun armsNotVisibleAskToShowThem() {
        val s = Sim()
        repeat(10) {
            s.now += 33
            s.last = s.tracker.process(TestFrames.personWithoutArms(s.now))
        }
        assertEquals(TrackingState.POSITIONING, s.last!!.tracking)
        assertEquals(TrackingMessage.SHOW_ARM, s.last!!.message)
    }

    @Test
    fun briefOcclusionDoesNotBreakCounting() {
        val s = Sim()
        s.reachCounting()
        s.rep()
        s.hold(null, 10) // ~330 ms, shorter than the grace period
        assertEquals(TrackingState.COUNTING, s.last!!.tracking)
        s.hold(170.0, 5)
        s.rep()
        assertEquals(2, s.last!!.reps)
        assertEquals(0, s.last!!.outOfFrameCount)
    }

    @Test
    fun leavingMidRepetitionDoesNotCountAFalseRepetition() {
        val s = Sim()
        s.reachCounting()
        s.rep()
        s.rep()
        assertEquals(2, s.last!!.reps)

        s.halfRep() // arm is flexed, repetition in progress
        s.hold(null, 60) // leaves the frame for ~2 s
        assertEquals(TrackingState.OUT_OF_FRAME, s.last!!.tracking)
        assertEquals(TrackingMessage.OUT_OF_FRAME, s.last!!.message)

        s.hold(170.0, 15) // comes back with the arm extended
        assertEquals(TrackingState.COUNTING, s.last!!.tracking)
        assertEquals("returning must not complete the interrupted repetition", 2, s.last!!.reps)

        s.rep() // a real repetition after returning is counted
        assertEquals(3, s.last!!.reps)
    }

    @Test
    fun returningInFlexedPhaseNeedsAFullRepetition() {
        val s = Sim()
        s.reachCounting()
        s.rep()
        s.hold(null, 60)
        s.hold(40.0, 15) // comes back with the arm already flexed
        assertEquals(1, s.last!!.reps)
        s.hold(170.0, 10) // extending now must not count: the repetition did not start from extended
        assertEquals(1, s.last!!.reps)
        s.rep()
        assertEquals(2, s.last!!.reps)
    }

    @Test
    fun outOfFrameTimeAndExitsAreReported() {
        val s = Sim()
        s.reachCounting()
        s.rep()
        s.hold(null, 60)
        s.hold(170.0, 15)
        val snap = s.last!!
        assertEquals(1, snap.outOfFrameCount)
        assertNotNull(snap.outOfFrameMs)
        assert(snap.outOfFrameMs in 1000..3000) { "unexpected out-of-frame time ${snap.outOfFrameMs}" }
    }

    @Test
    fun amplitudeIsReported() {
        val s = Sim()
        s.reachCounting()
        s.rep(bottom = 45.0)
        val snap = s.last!!
        assert(snap.minAngleDeg!! in 40.0..60.0) { "min ${snap.minAngleDeg}" }
        assert(snap.maxAngleDeg!! in 165.0..172.0) { "max ${snap.maxAngleDeg}" }
    }
}
