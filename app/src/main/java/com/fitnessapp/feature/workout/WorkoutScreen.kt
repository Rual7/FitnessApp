package com.fitnessapp.feature.workout

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.R
import com.fitnessapp.cv.camera.CameraView
import com.fitnessapp.cv.tracking.TrackingState
import com.fitnessapp.feature.exercises.Exercise

/**
 * The exercise screen: asks for the camera permission, then shows the live tracking.
 * [onFinish] is called after the set has been closed with the Finish button.
 */
@Composable
fun WorkoutScreen(exercise: Exercise, onBack: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            WorkoutHeader(title = stringResource(exercise.nameRes), onBack = onBack)
            if (hasPermission) {
                CameraSection(onFinish = onFinish)
            } else {
                PermissionRequest(onGrant = { launcher.launch(Manifest.permission.CAMERA) })
            }
        }
    }
}

@Composable
private fun WorkoutHeader(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun CameraSection(onFinish: () -> Unit, viewModel: WorkoutViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyzer = remember(viewModel) { ImageAnalysis.Analyzer { image -> viewModel.onCameraFrame(image) } }

    Column(Modifier.fillMaxWidth()) {
        // 3:4 portrait box; the preview and the overlay both fit-center the real frame inside it.
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            CameraView(analyzer = analyzer, modifier = Modifier.fillMaxSize())
            SkeletonOverlay(frame = state.frame)
            DebugPanel(state, Modifier.align(Alignment.TopStart))
            val snapshot = state.snapshot
            if (snapshot != null && snapshot.tracking == TrackingState.OUT_OF_FRAME) {
                OutOfFrameOverlay(snapshot, Modifier.align(Alignment.Center))
            }
        }
        RepetitionPanel(state.snapshot)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.reset_counter)) }
            Button(
                onClick = {
                    viewModel.finish()
                    onFinish()
                },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.workout_finish))
            }
        }
    }
}

@Composable
private fun PermissionRequest(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.camera_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onGrant) { Text(stringResource(R.string.camera_permission_grant)) }
    }
}
