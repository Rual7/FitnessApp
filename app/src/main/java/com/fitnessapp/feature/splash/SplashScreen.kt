package com.fitnessapp.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitnessapp.R
import kotlinx.coroutines.delay

private const val FILL_DURATION_MS = 1800
private const val HOLD_AFTER_FILL_MS = 300L

/**
 * Loading screen. It continues the system splash (same logo, same size, ring still empty) and
 * fills the ring's segments one after another before opening the app.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val fill = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        fill.animateTo(1f, tween(FILL_DURATION_MS, easing = FastOutSlowInEasing))
        delay(HOLD_AFTER_FILL_MS)
        finished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        // 176 dp = the logo size of the system splash screen (66/108 of its 288 dp icon area),
        // so the hand-over from the system splash is seamless.
        Box(Modifier.size(176.dp)) {
            ProgressRing(fill = fill.value, modifier = Modifier.fillMaxSize())
            Image(
                painter = painterResource(R.drawable.ic_logo_figure),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
