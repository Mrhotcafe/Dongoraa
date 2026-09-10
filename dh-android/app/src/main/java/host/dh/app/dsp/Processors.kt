package host.dh.app.dsp

import kotlin.math.*

/**
 * Real spectral-subtraction noise removal (NOT a gate). Estimates a per-bin
 * noise floor from the quietest frames and subtracts it in the frequency
 * domain, with an over-subtraction factor driven by [strength] and a
 * voice-band protection driven by [preserveVoice].
 */
class NoiseRemoval(
    var strength: Float = 0.6f,        // 0..1
    var preserveVoice: Float = 0.7f,   // 0..1 (protect ~200-4000 Hz)
) : Processor {
    override val name = "Noise Removal"
    override var bypass = false
    private val frame = 1024
    private val hop = 256

    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass) return
        for (c in channels.indices) {
            val out = processChannel(channels[c], sr)
            System.arraycopy(out, 0, channels[c], 0, channels[c].size)
        }
    }

    private fun processChannel(x: FloatArray, sr: Int): FloatArray {
        val n = x.size
        if (n < frame) return x
        val win = FloatArray(frame) { 0.5f - 0.5f * cos(2.0 * PI * it / (frame - 1)).toFloat() }
        val bins = frame / 2 + 1
        val nFrames = 1 + (n - frame) / hop
        if (nFrames < 4) return x
        val mags = Array(nFrames) { FloatArray(bins) }
        val phases = Array(nFrames) { FloatArray(bins) }
        val re = FloatArray(frame); val im = FloatArray(frame)
        for (fI in 0 until nFrames) {
            val off = fI * hop
            for (i in 0 until frame) { re[i] = x[off + i] * win[i]; im[i] = 0f }
            Fft.forward(re, im)
            for (b in 0 until bins) {
                mags[fI][b] = hypot(re[b], im[b])
                phases[fI][b] = atan2(im[b], re[b])
            }
        }
        // Per-bin noise floor = mean of the lowest 20% magnitudes.
        val noise = FloatArray(bins)
        val col = FloatArray(nFrames)
        val k = max(1, nFrames / 5)
        for (b in 0 until bins) {
            for (f in 0 until nFrames) col[f] = mags[f][b]
            col.sort()
            var s = 0f; for (i in 0 until k) s += col[i]
            noise[b] = s / k
        }
        // Subtract.
        val out = FloatArray(n)
        val wsum = FloatArray(n)
        val floorGain = 0.06f
        for (fI in 0 until nFrames) {
            for (b in 0 until bins) {
                val freq = b * sr.toFloat() / frame
                var alpha = 1f + strength * 3f
                if (freq in 200f..4000f) alpha *= (1f - 0.85f * preserveVoice)
                var mag = mags[fI][b] - alpha * noise[b]
                val fl = floorGain * mags[fI][b]
                if (mag < fl) mag = fl
                re[b] = mag * cos(phases[fI][b])
                im[b] = mag * sin(phases[fI][b])
                if (b in 1 until frame / 2) { re[frame - b] = re[b]; im[frame - b] = -im[b] }
            }
            Fft.inverse(re, im)
            val off = fI * hop
            for (i in 0 until frame) { out[off + i] += re[i] * win[i]; wsum[off + i] += win[i] * win[i] }
        }
        for (i in 0 until n) if (wsum[i] > 1e-6f) out[i] /= wsum[i] else out[i] = x[i]
        return out
    }
}

/** De-Esser: detects sibilance energy in a high band and ducks only that band. */
class DeEsser(
    var freq: Float = 6500f,
    var thresholdDb: Float = -30f,
    var amount: Float = 0.7f,     // 0..1 reduction strength
) : Processor {
    override val name = "De-Esser"
    override var bypass = false

    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass) return
        val atk = exp(-1f / (sr * 0.001f)); val rel = exp(-1f / (sr * 0.05f))
        for (c in channels.indices) {
            val x = channels[c]
            val hp = Biquad().apply { highpass(sr, freq, 0.7f) }
            val high = FloatArray(x.size)
            for (i in x.indices) high[i] = hp.processOne(x[i])
            var env = 0f
            val thr = db2lin(thresholdDb)
            for (i in x.indices) {
                val a = abs(high[i])
                env = if (a > env) atk * env + (1 - atk) * a else rel * env + (1 - rel) * a
                var g = 1f
                if (env > thr) g = 1f - amount * (1f - thr / env)
                // subtract the reduced portion of the high band
                x[i] = x[i] - high[i] * (1f - g)
            }
        }
    }
}

/**
 * Passive Pultec-style program EQ: simultaneous low shelf boost + low shelf cut
 * at the selected low frequency (their interaction produces the classic curve),
 * a high peaking boost with adjustable bandwidth, and a high shelf attenuation.
 */
class PultecEq(
    var lowFreq: Float = 60f,        // 20/30/60/100
    var lowBoost: Float = 0f,        // dB
    var lowCut: Float = 0f,          // dB (attenuation, positive value)
    var highBoostFreq: Float = 5000f,// 3k/5k/10k/16k
    var highBoost: Float = 0f,       // dB
    var bandwidth: Float = 1.2f,     // Q for high boost
    var highCutFreq: Float = 10000f, // 5k/10k/20k
    var highCut: Float = 0f,         // dB attenuation
) : Processor {
    override val name = "Pultec EQ"
    override var bypass = false

    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass) return
        for (c in channels.indices) {
            val x = channels[c]
            if (lowBoost != 0f) Biquad().apply { lowShelf(sr, lowFreq, lowBoost) }.processInPlace(x)
            if (lowCut != 0f) Biquad().apply { lowShelf(sr, lowFreq * 1.6f, -lowCut) }.processInPlace(x)
            if (highBoost != 0f) Biquad().apply { peaking(sr, highBoostFreq, bandwidth, highBoost) }.processInPlace(x)
            if (highCut != 0f) Biquad().apply { highShelf(sr, highCutFreq, -highCut) }.processInPlace(x)
        }
    }
}

/** Feed-forward compressor with soft knee, makeup gain and a gain-reduction meter. */
class Compressor(
    var thresholdDb: Float = -18f,
    var ratio: Float = 3f,
    var attackMs: Float = 20f,
    var releaseMs: Float = 150f,
    var kneeDb: Float = 6f,
    var makeupDb: Float = 0f,
    var mode: Mode = Mode.MEDIUM,
) : Processor {
    enum class Mode(val atk: Float, val rel: Float) { FAST(0.35f, 0.6f), MEDIUM(1f, 1f), SLOW(2.5f, 2.2f) }

    override val name = "Compressor"
    override var bypass = false
    var lastGrDb: Float = 0f; private set

    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass) return
        val atkS = (attackMs * mode.atk) / 1000f
        val relS = (releaseMs * mode.rel) / 1000f
        val aA = exp(-1f / (sr * max(1e-4f, atkS)))
        val aR = exp(-1f / (sr * max(1e-4f, relS)))
        val makeup = db2lin(makeupDb)
        var maxGr = 0f
        val frames = channels[0].size
        val env = FloatArray(channels.size)
        for (i in 0 until frames) {
            // linked detection across channels (max)
            var peak = 0f
            for (c in channels.indices) { val a = abs(channels[c][i]); if (a > peak) peak = a }
            val cIdx = 0
            env[cIdx] = if (peak > env[cIdx]) aA * env[cIdx] + (1 - aA) * peak else aR * env[cIdx] + (1 - aR) * peak
            val lvl = lin2db(env[cIdx])
            val over = lvl - thresholdDb
            val grDb: Float = when {
                over <= -kneeDb / 2 -> 0f
                over >= kneeDb / 2 -> over - over / ratio
                else -> {
                    val x = over + kneeDb / 2
                    ((1f / ratio - 1f) * x * x) / (2f * kneeDb) * -1f
                }
            }
            if (grDb > maxGr) maxGr = grDb
            val g = db2lin(-grDb) * makeup
            for (c in channels.indices) channels[c][i] *= g
        }
        lastGrDb = maxGr
    }
}

/** Simple output gain stage (dB). */
class Gain(var gainDb: Float = 0f) : Processor {
    override val name = "Gain"
    override var bypass = false
    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass || gainDb == 0f) return
        val g = db2lin(gainDb)
        for (c in channels.indices) { val x = channels[c]; for (i in x.indices) x[i] *= g }
    }
}

/** Look-ahead brick-wall peak limiter (channel-linked). */
class Limiter(
    var ceilingDb: Float = -1f,
    var releaseMs: Float = 60f,
    var lookaheadMs: Float = 5f,
) : Processor {
    override val name = "Limiter"
    override var bypass = false

    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass) return
        val ceiling = db2lin(ceilingDb)
        val la = max(1, (lookaheadMs / 1000f * sr).toInt())
        val aR = exp(-1f / (sr * max(1e-4f, releaseMs / 1000f)))
        val frames = channels[0].size
        // delayed copy for lookahead
        val delayed = Array(channels.size) { FloatArray(frames) }
        for (c in channels.indices) for (i in 0 until frames) delayed[c][i] = if (i >= la) channels[c][i - la] else 0f
        var gain = 1f
        for (i in 0 until frames) {
            var peakAhead = 0f
            for (c in channels.indices) { val a = abs(channels[c][i]); if (a > peakAhead) peakAhead = a }
            val target = if (peakAhead * gain > ceiling) ceiling / peakAhead else 1f
            gain = if (target < gain) target else aR * gain + (1 - aR) * target
            for (c in channels.indices) delayed[c][i] *= gain
        }
        for (c in channels.indices) System.arraycopy(delayed[c], 0, channels[c], 0, frames)
    }
}

/** Optional downward noise gate (additional processor, not the denoiser). */
class NoiseGate(
    var thresholdDb: Float = -50f,
    var attackMs: Float = 5f,
    var holdMs: Float = 40f,
    var releaseMs: Float = 120f,
) : Processor {
    override val name = "Noise Gate"
    override var bypass = true
    override fun process(channels: Array<FloatArray>, sr: Int) {
        if (bypass) return
        val thr = db2lin(thresholdDb)
        val aA = exp(-1f / (sr * attackMs / 1000f))
        val aR = exp(-1f / (sr * releaseMs / 1000f))
        val holdN = (holdMs / 1000f * sr).toInt()
        var env = 0f; var gain = 0f; var hold = 0
        val frames = channels[0].size
        for (i in 0 until frames) {
            var peak = 0f
            for (c in channels.indices) { val a = abs(channels[c][i]); if (a > peak) peak = a }
            env = if (peak > env) peak else 0.999f * env
            val open = env > thr
            if (open) { hold = holdN; gain = aA * gain + (1 - aA) * 1f }
            else if (hold > 0) { hold--; }
            else gain = aR * gain
            for (c in channels.indices) channels[c][i] *= gain
        }
    }
}
