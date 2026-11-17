package com.fitnessapp.feature.summary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitnessapp.R
import com.fitnessapp.cv.engine.SessionSummary
import com.fitnessapp.ui.formatDuration
import kotlin.math.roundToInt

private const val MAX_MANUAL_DIGITS = 3

/**
 * Totals of the finished set and the comparison with the number the user counted by hand.
 * The system back button behaves like [onNewSet]: the set is discarded and a new one starts.
 */
@Composable
fun SummaryScreen(summary: SessionSummary, onNewSet: () -> Unit, onDone: () -> Unit) {
    var manualText by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onNewSet)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.summary_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            RepetitionsCard(summary.reps)
            StatsCard(summary)
            ManualCountSection(
                summary = summary,
                manualText = manualText,
                onManualTextChange = { manualText = it.filter(Char::isDigit).take(MAX_MANUAL_DIGITS) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onNewSet,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.summary_new_set))
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.summary_done))
                }
            }
        }
    }
}

/** The main card of the screen, inverted like the theme's main card. */
@Composable
private fun RepetitionsCard(reps: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.summary_reps), style = MaterialTheme.typography.titleMedium)
            Text(reps.toString(), style = MaterialTheme.typography.displayLarge)
        }
    }
}

@Composable
private fun StatsCard(summary: SessionSummary) {
    val notAvailable = stringResource(R.string.summary_not_available)
    val pace = summary.averageRepMs?.let { stringResource(R.string.summary_pace_value, it / 1000f) } ?: notAvailable
    val range = if (summary.minAngleDeg != null && summary.maxAngleDeg != null) {
        stringResource(R.string.summary_range_value, summary.minAngleDeg.roundToInt(), summary.maxAngleDeg.roundToInt())
    } else {
        notAvailable
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            StatRow(stringResource(R.string.summary_duration), formatDuration(summary.durationMs))
            StatRow(stringResource(R.string.summary_active), formatDuration(summary.activeMs))
            StatRow(
                stringResource(R.string.summary_out_of_frame),
                stringResource(R.string.summary_out_of_frame_value, formatDuration(summary.outOfFrameMs), summary.outOfFrameCount),
            )
            StatRow(stringResource(R.string.summary_pace), pace)
            StatRow(stringResource(R.string.summary_range), range)
            StatRow(stringResource(R.string.summary_fps), stringResource(R.string.summary_fps_value, summary.averageFps), divider = false)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, divider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ManualCountSection(summary: SessionSummary, manualText: String, onManualTextChange: (String) -> Unit) {
    val manual = manualText.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.summary_manual_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        OutlinedTextField(
            value = manualText,
            onValueChange = onManualTextChange,
            label = { Text(stringResource(R.string.summary_manual_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        val difference = manual?.let(summary::differenceTo)
        Text(
            text = when {
                difference == null -> stringResource(R.string.summary_difference_hint)
                difference == 0 -> stringResource(R.string.summary_difference_same)
                difference > 0 -> stringResource(R.string.summary_difference_more, difference)
                else -> stringResource(R.string.summary_difference_fewer, difference)
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (difference == null) FontWeight.Normal else FontWeight.SemiBold,
            color = if (difference == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
        )
    }
}
