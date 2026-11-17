package com.fitnessapp.ui

import java.util.Locale

/** m:ss, e.g. 0:06. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
