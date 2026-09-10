package host.dh.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import kotlin.math.max

fun Modifier.clickableTag(tag: String, onClick: () -> Unit): Modifier =
    this.testTag(tag).clickable(onClick = onClick)

/** Horizontal peak level meter (0..1). */
@Composable
fun LevelMeter(peak: Float, rms: Float, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(SurfaceHi)
            val rmsW = size.width * rms.coerceIn(0f, 1f)
            drawRect(AccentDim, size = androidx.compose.ui.geometry.Size(rmsW, size.height))
            val peakW = size.width * peak.coerceIn(0f, 1f)
            val col = if (peak > 0.95f) Danger else Accent
            drawRect(col, topLeft = Offset(max(0f, peakW - 3f), 0f),
                size = androidx.compose.ui.geometry.Size(3f, size.height))
        }
    }
}

/** Scrolling waveform rendered from a bar buffer (each value 0..1). */
@Composable
fun WaveformView(bars: FloatArray, color: Color = Accent, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (bars.isEmpty()) return@Canvas
            val n = bars.size
            val gap = 2f
            val barW = (size.width - gap * (n - 1)) / n
            val midY = size.height / 2f
            for (i in 0 until n) {
                val h = (bars[i].coerceIn(0f, 1f)) * size.height
                val x = i * (barW + gap)
                drawRect(
                    color = color,
                    topLeft = Offset(x, midY - h / 2f),
                    size = androidx.compose.ui.geometry.Size(barW.coerceAtLeast(1f), h.coerceAtLeast(2f)),
                )
            }
        }
    }
}
