package com.fitnessapp.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitnessapp.R
import com.fitnessapp.cv.engine.WorkoutSnapshot
import com.fitnessapp.cv.pose.PoseLandmarks
import java.util.Locale

// Text drawn over the camera image must stay readable on any picture, so it does not follow the theme.
private val OnCamera = Color.White
private val ScrimLight = Color.Black.copy(alpha = 0.55f)
private val ScrimStrong = Color.Black.copy(alpha = 0.7f)

/** Debug readout: FPS, latency, elbow angle, tracking state and the visibility of the arm landmarks. */
@Composable
internal fun DebugPanel(state: WorkoutUiState, modifier: Modifier = Modifier) {
    val landmarks = state.frame?.landmarks
    val snapshot = state.snapshot
    fun visibility(index: Int) = landmarks?.get(index)?.visibility ?: 0f
    val style = MaterialTheme.typography.labelMedium

    Column(
        modifier
            .padding(8.dp)
            .background(ScrimLight, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        if (state.error != null) {
            Text(stringResource(R.string.pose_error, state.error), color = OnCamera, style = style)
        }
        Text(
            stringResource(R.string.debug_fps, state.cameraFps, state.poseFps, state.inferenceMs),
            color = OnCamera,
            style = style,
        )
        Text(
            stringResource(
                R.string.debug_angle,
                snapshot?.elbowAngleDeg?.let { String.format(Locale.ROOT, "%.0f°", it) }
                    ?: stringResource(R.string.debug_angle_none),
                snapshot?.tracking?.name ?: "-",
                snapshot?.phase?.name ?: "-",
            ),
            color = OnCamera,
            style = style,
        )
        Text(
            stringResource(if (landmarks != null) R.string.debug_person_yes else R.string.debug_person_no),
            color = OnCamera,
            style = style,
        )
        Text(
            stringResource(
                R.string.debug_visibility,
                visibility(PoseLandmarks.LEFT_SHOULDER), visibility(PoseLandmarks.LEFT_ELBOW), visibility(PoseLandmarks.LEFT_WRIST),
                visibility(PoseLandmarks.RIGHT_SHOULDER), visibility(PoseLandmarks.RIGHT_ELBOW), visibility(PoseLandmarks.RIGHT_WRIST),
            ),
            color = OnCamera,
            style = style,
        )
    }
}

/** Shown over the camera while the person is out of frame: message and the running absence time. */
@Composable
internal fun OutOfFrameOverlay(snapshot: WorkoutSnapshot, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(ScrimStrong, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.msg_out_of_frame), style = MaterialTheme.typography.headlineSmall, color = OnCamera)
        Text(stringResource(R.string.msg_out_of_frame_sub), style = MaterialTheme.typography.bodyLarge, color = OnCamera)
        Text(stringResource(R.string.msg_counting_paused), style = MaterialTheme.typography.bodyMedium, color = OnCamera)
        Text(formatDuration(snapshot.currentAbsenceMs), style = MaterialTheme.typography.headlineMedium, color = OnCamera)
    }
}
