package com.fitnessapp.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitnessapp.R
import com.fitnessapp.cv.engine.TrackingMessage
import com.fitnessapp.cv.engine.WorkoutSnapshot
import com.fitnessapp.ui.formatDuration
import kotlin.math.ceil

/** The big repetition counter, the current status message and the out-of-frame summary. */
@Composable
internal fun RepetitionPanel(snapshot: WorkoutSnapshot?) {
    val message = when (snapshot?.message) {
        null, TrackingMessage.ENTER_FRAME -> stringResource(R.string.msg_enter_frame)
        TrackingMessage.SHOW_ARM -> stringResource(R.string.msg_show_arm)
        TrackingMessage.STEP_BACK -> stringResource(R.string.msg_step_back)
        TrackingMessage.GET_READY -> {
            val seconds = ceil((snapshot.countdownRemainingMs ?: 0L) / 1000.0).toInt()
            stringResource(R.string.msg_get_ready, seconds)
        }
        TrackingMessage.COUNTING -> stringResource(R.string.msg_counting)
        TrackingMessage.OUT_OF_FRAME -> stringResource(R.string.msg_counting_paused)
        TrackingMessage.SUGGEST_END -> stringResource(R.string.msg_suggest_end)
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (snapshot?.reps ?: 0).toString(),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.reps_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
        )
        if (snapshot != null && snapshot.outOfFrameCount > 0) {
            Text(
                text = stringResource(
                    R.string.out_of_frame_summary,
                    snapshot.outOfFrameCount,
                    formatDuration(snapshot.outOfFrameMs),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
