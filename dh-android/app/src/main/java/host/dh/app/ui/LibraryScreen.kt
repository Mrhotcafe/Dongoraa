package host.dh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import host.dh.app.data.Recording
import host.dh.app.data.Recordings
import host.dh.app.playback.PlayerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(Recordings.list(ctx)) }
    val play by PlayerController.state.collectAsState()
    var renameFor by remember { mutableStateOf<Recording?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun refresh() { items = Recordings.list(ctx) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopAppBar(
            title = { Text("Library") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = OnBg,
                navigationIconContentColor = Accent),
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recordings yet", color = Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.file.absolutePath }) { rec ->
                    RecordingCard(
                        rec = rec,
                        isCurrent = play.path == rec.file.absolutePath,
                        playState = play,
                        onToggle = { PlayerController.toggle(rec.file) },
                        onSeek = { PlayerController.seekTo(it) },
                        onRename = { renameFor = rec; renameText = rec.name },
                        onDelete = { if (Recordings.delete(rec)) { if (play.path == rec.file.absolutePath) PlayerController.stop(); refresh() } },
                        onShare = { Recordings.share(ctx, rec) },
                        onEdit = { onEdit(rec.file.name) },
                    )
                }
            }
        }
    }

    renameFor?.let { rec ->
        AlertDialog(
            onDismissRequest = { renameFor = null },
            confirmButton = {
                TextButton(onClick = {
                    Recordings.rename(rec, renameText); renameFor = null
                    PlayerController.stop(); refresh()
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Cancel") } },
            title = { Text("Rename recording") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                    singleLine = true, label = { Text("Name") })
            },
        )
    }
}

@Composable
private fun RecordingCard(
    rec: Recording,
    isCurrent: Boolean,
    playState: host.dh.app.playback.PlayState,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(if (isCurrent && playState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "play", tint = Accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.name, color = OnBg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                Text(
                    "${formatTime(rec.durationMs)}  ·  ${rec.sampleRate / 1000}kHz ${if (rec.channels == 2) "stereo" else "mono"}  ·  ${rec.sizeBytes / 1024}KB" +
                        if (!rec.valid) "  ·  ⚠ invalid" else "",
                    color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (isCurrent && playState.durationMs > 0) {
            Slider(
                value = playState.positionMs.toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..playState.durationMs.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AssistChip(onClick = onEdit, label = { Text("Edit DSP") },
                leadingIcon = { Icon(Icons.Filled.GraphicEq, null, Modifier.size(18.dp)) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "rename", tint = Muted) }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "share", tint = Muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "delete", tint = Danger) }
        }
    }
}
