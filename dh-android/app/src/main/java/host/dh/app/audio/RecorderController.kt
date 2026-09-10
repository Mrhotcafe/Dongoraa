package host.dh.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

data class RecordingConfig(
    val sampleRate: Int = 48_000,
    val channels: Int = 1,           // 1 = mono, 2 = stereo
)

sealed class RecState {
    data object Idle : RecState()
    data class Active(
        val paused: Boolean,
        val elapsedMs: Long,
        val peak: Float,             // 0..1
        val rms: Float,              // 0..1
        val path: String,
    ) : RecState()

    data class Error(val message: String) : RecState()
}

/**
 * Owns the real [AudioRecord] capture loop and streams PCM straight into a
 * [WavWriter]. Exposed as a process singleton so the UI and the foreground
 * [RecordingService] share one source of truth. No synthetic audio is injected;
 * on hardware without a live mic the captured samples are simply silent.
 */
object RecorderController {
    private const val WAVE_BARS = 90

    val state = MutableStateFlow<RecState>(RecState.Idle)
    val waveform = MutableStateFlow(FloatArray(WAVE_BARS))

    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var writer: WavWriter? = null
    private var outFile: File? = null
    private var cfg: RecordingConfig = RecordingConfig()
    private val bars = FloatArray(WAVE_BARS)

    val isActive: Boolean get() = running

    fun recordingsDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "Recordings").apply { mkdirs() }

    /** Called by the UI. Boots the foreground service which then begins capture. */
    fun start(ctx: Context, config: RecordingConfig) {
        if (running) return
        cfg = config
        val name = "REC_" + android.text.format.DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".wav"
        outFile = File(recordingsDir(ctx), name)
        val i = Intent(ctx, RecordingService::class.java).setAction(RecordingService.ACTION_START)
        ContextCompat.startForegroundService(ctx, i)
    }

    /** Invoked by the service once it is in the foreground. */
    @SuppressLint("MissingPermission")
    internal fun beginCapture() {
        if (running) return
        val file = outFile ?: return
        val channelCfg = if (cfg.channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuf = AudioRecord.getMinBufferSize(cfg.sampleRate, channelCfg, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { state.value = RecState.Error("Audio not available on this device"); return }
        val frameChunk = cfg.sampleRate / 10                 // ~100 ms
        val bufShorts = maxOf(minBuf / 2, frameChunk * cfg.channels)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, cfg.sampleRate, channelCfg,
                AudioFormat.ENCODING_PCM_16BIT, bufShorts * 2)
        } catch (e: Exception) { state.value = RecState.Error("Recorder init failed: ${e.message}"); return }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            state.value = RecState.Error("Recorder could not initialise"); record.release(); return
        }
        val w = WavWriter(file, cfg.sampleRate, cfg.channels).also { it.open() }
        writer = w
        java.util.Arrays.fill(bars, 0f)
        running = true; paused = false
        var framesWritten = 0L
        state.value = RecState.Active(false, 0, 0f, 0f, file.absolutePath)
        record.startRecording()
        thread = Thread {
            val buf = ShortArray(bufShorts)
            var barIdx = 0
            try {
                while (running) {
                    val n = record.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (paused) continue
                    w.writeShorts(buf, n)
                    var peak = 0; var sumSq = 0.0
                    var i = 0
                    while (i < n) { val s = buf[i].toInt(); val a = abs(s); if (a > peak) peak = a; sumSq += s.toDouble() * s; i++ }
                    val peakN = peak / 32768f
                    val rmsN = (sqrt(sumSq / n) / 32768.0).toFloat()
                    framesWritten += n / cfg.channels
                    // scrolling waveform
                    System.arraycopy(bars, 1, bars, 0, WAVE_BARS - 1)
                    bars[WAVE_BARS - 1] = peakN
                    waveform.value = bars.copyOf()
                    state.value = RecState.Active(false, framesWritten * 1000L / cfg.sampleRate, peakN, rmsN, file.absolutePath)
                    barIdx++
                }
            } catch (e: Exception) {
                state.value = RecState.Error(e.message ?: "capture error")
            } finally {
                try { record.stop() } catch (_: Exception) {}
                record.release()
            }
        }.apply { name = "dh-capture"; start() }
    }

    fun pause() {
        if (!running) return
        paused = true
        (state.value as? RecState.Active)?.let { state.value = it.copy(paused = true) }
    }

    fun resume() {
        if (!running) return
        paused = false
        (state.value as? RecState.Active)?.let { state.value = it.copy(paused = false) }
    }

    /** Stops capture, finalises the WAV and returns the file path (or null). */
    fun stop(ctx: Context): String? {
        if (!running) return null
        running = false
        try { thread?.join(1500) } catch (_: Exception) {}
        thread = null
        val f = writer?.finish()
        writer = null
        state.value = RecState.Idle
        waveform.value = FloatArray(WAVE_BARS)
        ctx.stopService(Intent(ctx, RecordingService::class.java))
        return f?.absolutePath
    }
}
