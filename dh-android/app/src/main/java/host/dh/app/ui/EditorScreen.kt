package host.dh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import host.dh.app.data.Recordings
import host.dh.app.dsp.Compressor
import host.dh.app.dsp.DspChain
import host.dh.app.playback.PlayerController
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(fileName: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val src = File(Recordings.dir(ctx), fileName)
    val chain = remember { DspChain() }
    var tick by remember { mutableIntStateOf(0) }
    fun changed() { tick++ }
    var rendering by remember { mutableStateOf(false) }
    var processed by remember { mutableStateOf<File?>(null) }
    var tests by remember { mutableStateOf<List<Triple<String, Boolean, String>>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    val play by PlayerController.state.collectAsState()

    fun renderNow() {
        rendering = true; status = "Rendering…"
        Thread {
            val dst = File(Recordings.processedDir(ctx), src.nameWithoutExtension + "_processed.wav")
            val ok = try { chain.render(src, dst) } catch (e: Exception) { null }
            processed = ok
            status = if (ok != null) "Rendered → ${dst.name}  (original preserved)" else "Render failed"
            rendering = false
        }.start()
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopAppBar(
            title = { Text("DSP Editor", fontSize = 18.sp) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = OnBg,
                navigationIconContentColor = Accent),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
            @Suppress("UNUSED_EXPRESSION") tick   // recompose readouts/bypass/chips on discrete changes
            Text(src.nameWithoutExtension, color = OnBg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Non-destructive: edits render to a new file; the original stays intact.",
                color = Muted, fontSize = 11.sp)
            if (tests.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(12.dp)) {
                    Text("DSP self-tests", color = OnBg, fontWeight = FontWeight.SemiBold)
                    tests.forEach { (n, ok, d) ->
                        Text("${if (ok) "✓" else "✗"} $n — $d", color = if (ok) Accent else Danger,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            run {
                // Chain order: Gate → Noise Removal → De-Esser → Pultec EQ → Compressor → Gain → Limiter
                ProcCard("Noise Gate (optional)", chain.gate.bypass, { chain.gate.bypass = it; changed() }) {
                    ParamSlider("Threshold", chain.gate.thresholdDb, -80f, 0f, "dB") { chain.gate.thresholdDb = it; changed() }
                    ParamSlider("Attack", chain.gate.attackMs, 1f, 50f, "ms") { chain.gate.attackMs = it; changed() }
                    ParamSlider("Hold", chain.gate.holdMs, 0f, 500f, "ms") { chain.gate.holdMs = it; changed() }
                    ParamSlider("Release", chain.gate.releaseMs, 10f, 1000f, "ms") { chain.gate.releaseMs = it; changed() }
                }
                ProcCard("Noise Removal", chain.noise.bypass, { chain.noise.bypass = it; changed() }) {
                    ParamSlider("Strength", chain.noise.strength, 0f, 1f, "") { chain.noise.strength = it; changed() }
                    ParamSlider("Preserve Voice", chain.noise.preserveVoice, 0f, 1f, "") { chain.noise.preserveVoice = it; changed() }
                }
                ProcCard("De-Esser", chain.deEsser.bypass, { chain.deEsser.bypass = it; changed() }) {
                    ParamSlider("Frequency", chain.deEsser.freq, 2000f, 12000f, "Hz") { chain.deEsser.freq = it; changed() }
                    ParamSlider("Threshold", chain.deEsser.thresholdDb, -60f, 0f, "dB") { chain.deEsser.thresholdDb = it; changed() }
                    ParamSlider("Amount", chain.deEsser.amount, 0f, 1f, "") { chain.deEsser.amount = it; changed() }
                }
                ProcCard("Pultec EQ", chain.pultec.bypass, { chain.pultec.bypass = it; changed() }) {
                    ChipRow("Low Freq", listOf(20f, 30f, 60f, 100f), chain.pultec.lowFreq, "Hz") { chain.pultec.lowFreq = it; changed() }
                    ParamSlider("Low Boost", chain.pultec.lowBoost, 0f, 12f, "dB") { chain.pultec.lowBoost = it; changed() }
                    ParamSlider("Low Atten", chain.pultec.lowCut, 0f, 12f, "dB") { chain.pultec.lowCut = it; changed() }
                    ChipRow("High Freq", listOf(3000f, 5000f, 10000f, 16000f), chain.pultec.highBoostFreq, "Hz") { chain.pultec.highBoostFreq = it; changed() }
                    ParamSlider("High Boost", chain.pultec.highBoost, 0f, 12f, "dB") { chain.pultec.highBoost = it; changed() }
                    ParamSlider("Bandwidth", chain.pultec.bandwidth, 0.3f, 3f, "Q") { chain.pultec.bandwidth = it; changed() }
                    ChipRow("HiCut Freq", listOf(5000f, 10000f, 20000f), chain.pultec.highCutFreq, "Hz") { chain.pultec.highCutFreq = it; changed() }
                    ParamSlider("High Atten", chain.pultec.highCut, 0f, 12f, "dB") { chain.pultec.highCut = it; changed() }
                }
                ProcCard("Compressor", chain.compressor.bypass, { chain.compressor.bypass = it; changed() }) {
                    ParamSlider("Threshold", chain.compressor.thresholdDb, -60f, 0f, "dB") { chain.compressor.thresholdDb = it; changed() }
                    ParamSlider("Ratio", chain.compressor.ratio, 1f, 20f, ":1") { chain.compressor.ratio = it; changed() }
                    ParamSlider("Attack", chain.compressor.attackMs, 1f, 100f, "ms") { chain.compressor.attackMs = it; changed() }
                    ParamSlider("Release", chain.compressor.releaseMs, 10f, 1000f, "ms") { chain.compressor.releaseMs = it; changed() }
                    ParamSlider("Knee", chain.compressor.kneeDb, 0f, 24f, "dB") { chain.compressor.kneeDb = it; changed() }
                    ParamSlider("Makeup", chain.compressor.makeupDb, 0f, 24f, "dB") { chain.compressor.makeupDb = it; changed() }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Compressor.Mode.values().forEach { m ->
                            FilterChip(selected = chain.compressor.mode == m, onClick = { chain.compressor.mode = m; changed() },
                                label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Gain reduction: %.1f dB".format(chain.compressor.lastGrDb), color = Accent, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace)
                    LevelMeter((chain.compressor.lastGrDb / 24f).coerceIn(0f, 1f), 0f,
                        Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp))
                }
                ProcCard("Gain", chain.gain.bypass, { chain.gain.bypass = it; changed() }) {
                    ParamSlider("Output Gain", chain.gain.gainDb, -24f, 24f, "dB") { chain.gain.gainDb = it; changed() }
                }
                ProcCard("Limiter", chain.limiter.bypass, { chain.limiter.bypass = it; changed() }) {
                    ParamSlider("Ceiling", chain.limiter.ceilingDb, -24f, 0f, "dB") { chain.limiter.ceilingDb = it; changed() }
                    ParamSlider("Release", chain.limiter.releaseMs, 1f, 500f, "ms") { chain.limiter.releaseMs = it; changed() }
                    ParamSlider("Lookahead", chain.limiter.lookaheadMs, 0f, 20f, "ms") { chain.limiter.lookaheadMs = it; changed() }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // Action bar
        Column(Modifier.background(Surface).padding(12.dp)) {
            if (status.isNotEmpty()) Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { PlayerController.toggle(src) }, modifier = Modifier.weight(1f)) {
                    Text(if (play.path == src.absolutePath && play.playing) "❚❚ Orig" else "▶ Original")
                }
                OutlinedButton(onClick = { processed?.let { PlayerController.toggle(it) } },
                    enabled = processed != null, modifier = Modifier.weight(1f)) {
                    Text(if (processed != null && play.path == processed!!.absolutePath && play.playing) "❚❚ Proc" else "▶ Processed")
                }
                Button(onClick = { renderNow() }, enabled = !rendering, modifier = Modifier.weight(1f),
                    testID = "renderBtn") {
                    Text(if (rendering) "…" else "Render")
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = {
                val r = DspChain.selfTest(); tests = r
                val fails = r.filter { !it.second }.joinToString(", ") { it.first.substringBefore(" ") }
                status = "DSP self-tests: ${r.count { it.second }}/${r.size} passed" +
                    if (fails.isNotEmpty()) " — fail: $fails" else " ✓"
            }) { Text("Run DSP self-tests", color = Accent) }
        }
    }
}

/** helper button with a testTag */
@Composable
private fun Button(onClick: () -> Unit, enabled: Boolean, modifier: Modifier, testID: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick = onClick, enabled = enabled,
        modifier = modifier.clipTag(testID), content = content)
}

private fun Modifier.clipTag(tag: String): Modifier = this.testTag(tag)

@Composable
private fun ProcCard(title: String, bypassed: Boolean, onBypass: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
        .background(if (bypassed) SurfaceHi.copy(alpha = 0.4f) else Surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (bypassed) Muted else OnBg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Text(if (bypassed) "Bypassed" else "Active", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Switch(checked = !bypassed, onCheckedChange = { onBypass(!it) })
        }
        if (!bypassed) { Spacer(Modifier.height(4.dp)); content() }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, min: Float, max: Float, unit: String, onChange: (Float) -> Unit) {
    var v by remember { mutableStateOf(value) }
    Column {
        Row {
            Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(fmt(v, unit), color = OnBg, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(value = v, onValueChange = { v = it; onChange(it) }, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = AccentDim))
    }
}

@Composable
private fun ChipRow(label: String, options: List<Float>, current: Float, unit: String, onPick: (Float) -> Unit) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = current == o, onClick = { onPick(o) },
                    label = { Text(fmt(o, unit), fontSize = 12.sp) })
            }
        }
    }
}

private fun fmt(v: Float, unit: String): String {
    val s = when {
        unit == "Hz" && v >= 1000f -> "%.1fk".format(v / 1000f)
        unit == "" || unit == ":1" || unit == "Q" -> "%.2f".format(v)
        else -> "%.0f".format(v)
    }
    return if (unit == "Hz" || unit == "") "$s${if (unit == "Hz") "" else ""}" else "$s $unit".trim()
}
