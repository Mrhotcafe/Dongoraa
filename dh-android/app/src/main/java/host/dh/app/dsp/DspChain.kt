package host.dh.app.dsp

import host.dh.app.audio.WavIo
import host.dh.app.audio.WavWriter
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*

/**
 * Ordered, non-destructive processing chain:
 * Noise Gate (optional) → Noise Removal → De-Esser → Pultec EQ → Compressor → Gain → Limiter.
 * [render] always writes to a NEW file, never touching the source recording.
 */
class DspChain {
    val gate = NoiseGate()
    val noise = NoiseRemoval()
    val deEsser = DeEsser()
    val pultec = PultecEq()
    val compressor = Compressor()
    val gain = Gain()
    val limiter = Limiter()

    val processors: List<Processor> = listOf(gate, noise, deEsser, pultec, compressor, gain, limiter)

    fun processChannels(channels: Array<FloatArray>, sr: Int) {
        for (p in processors) if (!p.bypass) p.process(channels, sr)
    }

    /** Render source WAV through the chain into destFile. Returns dest on success. */
    fun render(src: File, dest: File): File? {
        val info = WavIo.readInfo(src)
        if (!info.valid) return null
        val raw = ByteArray(info.dataBytes.toInt())
        RandomAccessFile(src, "r").use { r -> r.seek(info.dataOffset); r.readFully(raw) }
        val channels = PcmFloat.fromPcm16(raw, raw.size, info.channels)
        processChannels(channels, info.sampleRate)
        val out = PcmFloat.toPcm16(channels)
        val w = WavWriter(dest, info.sampleRate, info.channels).also { it.open() }
        w.writeBytes(out, out.size)
        w.finish()
        return dest
    }

    companion object {
        /** Numerical self-tests — the DSP proof runnable on the emulator. */
        fun selfTest(): List<Triple<String, Boolean, String>> {
            val sr = 48000
            val res = ArrayList<Triple<String, Boolean, String>>()

            fun sine(freq: Float, amp: Float, n: Int) = FloatArray(n) { amp * sin(2.0 * PI * freq * it / sr).toFloat() }
            fun rms(x: FloatArray): Float { var s = 0.0; for (v in x) s += v.toDouble() * v; return sqrt(s / x.size).toFloat() }
            fun peak(x: FloatArray): Float { var p = 0f; for (v in x) p = max(p, abs(v)); return p }

            // 1. Gain +6 dB ≈ ×2 amplitude
            run {
                val x = sine(1000f, 0.2f, sr / 2); val before = rms(x)
                Gain(6f).process(arrayOf(x), sr); val ratio = rms(x) / before
                res.add(Triple("Gain +6dB doubles level", abs(ratio - 2f) < 0.05f, "ratio=%.3f".format(ratio)))
            }
            // 2. Limiter enforces ceiling
            run {
                val x = sine(1000f, 1.0f, sr / 2)
                Limiter(ceilingDb = -3f).process(arrayOf(x), sr)
                val pk = lin2db(peak(x))
                res.add(Triple("Limiter holds -3dB ceiling", pk <= -2.5f, "peak=%.2fdB".format(pk)))
            }
            // 3. Compressor reduces level above threshold
            run {
                val x = sine(1000f, 0.7f, sr / 2); val before = rms(x)
                val comp = Compressor(thresholdDb = -20f, ratio = 4f, attackMs = 5f, releaseMs = 50f, kneeDb = 2f)
                comp.process(arrayOf(x), sr)
                val quieter = rms(x) < before
                res.add(Triple("Compressor 4:1 reduces + GR meter", quieter && comp.lastGrDb > 1f,
                    "GR=%.1fdB".format(comp.lastGrDb)))
            }
            // 4. Pultec high boost raises HF energy
            run {
                val lo = sine(200f, 0.3f, sr / 2); val hi = sine(8000f, 0.3f, sr / 2)
                val x = FloatArray(lo.size) { lo[it] + hi[it] }
                val hpRef = Biquad().apply { highpass(sr, 5000f, 0.7f) }
                val bRef = FloatArray(x.size) { hpRef.processOne(x[it]) }; val hfBefore = rms(bRef)
                PultecEq(highBoostFreq = 8000f, highBoost = 8f, bandwidth = 1.0f).process(arrayOf(x), sr)
                val hpA = Biquad().apply { highpass(sr, 5000f, 0.7f) }
                val bA = FloatArray(x.size) { hpA.processOne(x[it]) }; val hfAfter = rms(bA)
                res.add(Triple("Pultec HF boost raises highs", hfAfter > hfBefore * 1.2f,
                    "HF %.3f→%.3f".format(hfBefore, hfAfter)))
            }
            // 5. Noise removal lowers broadband noise floor
            run {
                val rnd = java.util.Random(1)
                val tone = sine(1000f, 0.25f, sr)
                val x = FloatArray(tone.size) { tone[it] + (rnd.nextFloat() - 0.5f) * 0.08f }
                val hp = Biquad().apply { highpass(sr, 12000f, 0.7f) }
                val nb = FloatArray(x.size) { hp.processOne(x[it]) }; val noiseBefore = rms(nb)
                NoiseRemoval(strength = 0.9f, preserveVoice = 0.3f).process(arrayOf(x), sr)
                val hp2 = Biquad().apply { highpass(sr, 12000f, 0.7f) }
                val na = FloatArray(x.size) { hp2.processOne(x[it]) }; val noiseAfter = rms(na)
                res.add(Triple("Noise removal lowers HF noise", noiseAfter < noiseBefore * 0.9f,
                    "HFnoise %.4f→%.4f".format(noiseBefore, noiseAfter)))
            }
            // 6. De-esser reduces sibilant band
            run {
                val voice = sine(500f, 0.3f, sr / 2); val ess = sine(7000f, 0.4f, sr / 2)
                val x = FloatArray(voice.size) { voice[it] + ess[it] }
                val hpRef = Biquad().apply { highpass(sr, 6000f, 0.7f) }
                val bRef = FloatArray(x.size) { hpRef.processOne(x[it]) }; val before = rms(bRef)
                DeEsser(freq = 6500f, thresholdDb = -40f, amount = 0.9f).process(arrayOf(x), sr)
                val hpA = Biquad().apply { highpass(sr, 6000f, 0.7f) }
                val bA = FloatArray(x.size) { hpA.processOne(x[it]) }; val after = rms(bA)
                res.add(Triple("De-esser ducks sibilance", after < before * 0.9f, "ess %.3f→%.3f".format(before, after)))
            }
            return res
        }
    }
}
