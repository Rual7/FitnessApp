package com.fitnessapp.feature.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.fitnessapp.cv.pose.PoseFrame
import com.fitnessapp.cv.pose.PoseLandmarks
import com.fitnessapp.ui.theme.AccentAmberDark
import com.fitnessapp.ui.theme.DarkOnBackground
import kotlin.math.min

/** Landmarks below this visibility are not drawn. Starting value, to tune on real recordings. */
private const val DRAW_MIN_VISIBILITY = 0.5f

/**
 * Draws the skeleton over the camera preview. The preview uses FIT_CENTER, so the same
 * fit-center rectangle is computed here from the real frame size; this keeps the skeleton aligned
 * whether the frame arrives in portrait or landscape. The x axis is flipped because the front
 * camera preview is shown like a mirror.
 */
@Composable
fun SkeletonOverlay(frame: PoseFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val landmarks = frame?.landmarks ?: return@Canvas
        if (frame.imageWidth <= 0 || frame.imageHeight <= 0) return@Canvas

        val scale = min(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val drawnW = frame.imageWidth * scale
        val drawnH = frame.imageHeight * scale
        val left = (size.width - drawnW) / 2f
        val top = (size.height - drawnH) / 2f

        fun point(i: Int): Offset {
            val l = landmarks[i]
            return Offset(left + (1f - l.x) * drawnW, top + l.y * drawnH)
        }

        val line = DarkOnBackground
        val strokeWidth = 3.dp.toPx()
        for ((a, b) in PoseLandmarks.CONNECTIONS) {
            if (landmarks[a].visibility < DRAW_MIN_VISIBILITY || landmarks[b].visibility < DRAW_MIN_VISIBILITY) continue
            drawLine(line, point(a), point(b), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        for (i in PoseLandmarks.FIRST_BODY_INDEX until landmarks.size) {
            if (landmarks[i].visibility < DRAW_MIN_VISIBILITY) continue
            drawCircle(AccentAmberDark, radius = 4.dp.toPx(), center = point(i))
        }
    }
}
