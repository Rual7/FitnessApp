package com.fitnessapp.cv.metrics

import com.fitnessapp.cv.pose.Landmark
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.PI

object AngleMath {

    /**
     * Angle in degrees (0..180) at point [b] between segments b->a and b->c, in 2D.
     * Coordinates are normalized to the image, so they are scaled by the image size first;
     * otherwise a 4:3 frame would distort the angles.
     */
    fun angleDegrees(a: Landmark, b: Landmark, c: Landmark, imageWidth: Int, imageHeight: Int): Double {
        val v1x = (a.x - b.x).toDouble() * imageWidth
        val v1y = (a.y - b.y).toDouble() * imageHeight
        val v2x = (c.x - b.x).toDouble() * imageWidth
        val v2y = (c.y - b.y).toDouble() * imageHeight
        val n1 = hypot(v1x, v1y)
        val n2 = hypot(v2x, v2y)
        if (n1 == 0.0 || n2 == 0.0) return Double.NaN
        val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return acos(cos) * 180.0 / PI
    }
}
