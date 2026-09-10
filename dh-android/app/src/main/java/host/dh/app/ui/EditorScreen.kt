package host.dh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import host.dh.app.data.Recordings
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(fileName: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val file = File(Recordings.dir(ctx), fileName)
    val rec = if (file.exists()) Recordings.toRecording(file) else null

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopAppBar(
            title = { Text("DSP Editor") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = OnBg,
                navigationIconContentColor = Accent),
        )
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rec?.name ?: "Missing file", color = OnBg, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Non-destructive processing chain arrives next.",
                    color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
