package host.dh.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import host.dh.app.audio.RecState
import host.dh.app.audio.RecordingConfig
import host.dh.app.audio.RecorderController

@Composable
fun RecordScreen(onOpenLibrary: () -> Unit) {
    val ctx = LocalContext.current
    val state by RecorderController.state.collectAsState()
    val bars by RecorderController.waveform.collectAsState()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var stereo by remember { mutableStateOf(false) }
    var hiRate by remember { mutableStateOf(true) } // true=48k, false=44.1k

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) startRecording(ctx, stereo, hiRate)
    }

    val active = state as? RecState.Active
    val recording = active != null

    Column(
        Modifier.fillMaxSize().background(Bg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("DH Recorder", color = OnBg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenLibrary) {
                Icon(Icons.Filled.LibraryMusic, null, tint = Accent)
                Spacer(Modifier.width(6.dp)); Text("Library", color = Accent)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Timer
        Text(
            formatTime(active?.elapsedMs ?: 0L),
            color = OnBg, fontSize = 56.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace,
        )
        Text(
            when {
                active?.paused == true -> "Paused"
                recording -> "Recording"
                else -> "Ready"
            },
            color = if (active?.paused == true) Muted else if (recording) Accent else Muted, fontSize = 14.sp,
        )

        Spacer(Modifier.height(20.dp))

        // Waveform
        WaveformView(
            bars = bars,
            modifier = Modifier.fillMaxWidth().height(120.dp)
                .clip(RoundedCornerShape(12.dp)).background(Surface).padding(8.dp),
        )
        Spacer(Modifier.height(12.dp))
        // Level meter
        LevelMeter(active?.peak ?: 0f, active?.rms ?: 0f, Modifier.fillMaxWidth().height(10.dp))

        Spacer(Modifier.height(20.dp))

        // Format chips (disabled while recording)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !stereo, onClick = { if (!recording) stereo = false }, enabled = !recording,
                label = { Text("Mono") })
            FilterChip(selected = stereo, onClick = { if (!recording) stereo = true }, enabled = !recording,
                label = { Text("Stereo") })
            Spacer(Modifier.width(4.dp))
            FilterChip(selected = hiRate, onClick = { if (!recording) hiRate = true }, enabled = !recording,
                label = { Text("48 kHz") })
            FilterChip(selected = !hiRate, onClick = { if (!recording) hiRate = false }, enabled = !recording,
                label = { Text("44.1 kHz") })
        }

        Spacer(Modifier.weight(1f))

        if (state is RecState.Error) {
            Text((state as RecState.Error).message, color = Danger, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (denied && !granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Microphone access is needed to record.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:host.dh.app")))
                }) { Text("Open Settings") }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Transport row
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (recording) {
                CircleBtn(if (active?.paused == true) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = if (active?.paused == true) "Resume" else "Pause", tint = Accent, testTag = "pauseResume") {
                    if (active?.paused == true) RecorderController.resume() else RecorderController.pause()
                }
            }
            BigRecordButton(recording = recording, testTag = if (recording) "stopBtn" else "recordBtn") {
                if (recording) {
                    RecorderController.stop(ctx)
                } else {
                    if (granted) startRecording(ctx, stereo, hiRate)
                    else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (recording) Spacer(Modifier.width(52.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun startRecording(ctx: android.content.Context, stereo: Boolean, hiRate: Boolean) {
    RecorderController.start(ctx, RecordingConfig(
        sampleRate = if (hiRate) 48_000 else 44_100,
        channels = if (stereo) 2 else 1,
    ))
}

@Composable
private fun BigRecordButton(recording: Boolean, testTag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(96.dp).clip(CircleShape)
            .background(if (recording) Surface else Danger)
            .border(3.dp, if (recording) Danger else Accent, CircleShape)
            .clickableTag(testTag, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, "record",
            tint = if (recording) Danger else OnBg, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun CircleBtn(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, testTag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(SurfaceHi).clickableTag(testTag, onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = tint) }
        Spacer(Modifier.height(4.dp)); Text(label, color = Muted, fontSize = 11.sp)
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val cs = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(m, s, cs)
}
