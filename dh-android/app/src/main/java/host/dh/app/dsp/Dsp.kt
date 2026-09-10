package host.dh.app.dsp

import kotlin.math.*

/** Base processor operating offline on deinterleaved float channels [-1,1]. */
interface Processor {
    val name: String
    var bypass: Boolean
    fun process(channels: Array<FloatArray>, sr: Int)
}

fun db2lin(db: Float) = 10f.pow(db / 20f)
fun lin2db(x: Float) = if (x <= 1e-9f) -180f else 20f * log10(x)

object PcmFloat {
    /** Interleaved PCM16 bytes -> per-channel float arrays. */
    fun fromPcm16(bytes: ByteArray, count: Int, channels: Int): Array<FloatArray> {
        val frames = (count / 2) / channels
        val out = Array(channels) { FloatArray(frames) }
        var bi = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val lo = bytes[bi].toInt() and 0xFF
                val hi = bytes[bi + 1].toInt()
                val s = (hi shl 8) or lo
                out[c][f] = s / 32768f
                bi += 2
            }
        }
        return out
    }

    /** Per-channel floats -> interleaved PCM16 bytes. */
    fun toPcm16(channels: Array<FloatArray>): ByteArray {
        val ch = channels.size
        val frames = channels[0].size
        val out = ByteArray(frames * ch * 2)
        var bi = 0
        for (f in 0 until frames) {
            for (c in 0 until ch) {
                var v = (channels[c][f] * 32768f).roundToInt()
                if (v > 32767) v = 32767; if (v < -32768) v = -32768
                out[bi] = (v and 0xFF).toByte()
                out[bi + 1] = ((v shr 8) and 0xFF).toByte()
                bi += 2
            }
        }
        return out
    }
}

/** Iterative radix-2 Cooley–Tukey FFT (in-place). n must be a power of two. */
object Fft {
    fun forward(re: FloatArray, im: FloatArray) = transform(re, im, false)
    fun inverse(re: FloatArray, im: FloatArray) {
        transform(re, im, true)
        val n = re.size
        val inv = 1f / n
        for (i in 0 until n) { re[i] *= inv; im[i] *= inv }
    }

    private fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = (if (inverse) 2.0 else -2.0) * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Transposed-direct-form-II biquad with RBJ cookbook coefficients. */
class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f

    fun reset() { z1 = 0f; z2 = 0f }

    fun processInPlace(x: FloatArray) {
        for (i in x.indices) {
            val inp = x[i]
            val out = b0 * inp + z1
            z1 = b1 * inp - a1 * out + z2
            z2 = b2 * inp - a2 * out
            x[i] = out
        }
    }

    fun processOne(inp: Float): Float {
        val out = b0 * inp + z1
        z1 = b1 * inp - a1 * out + z2
        z2 = b2 * inp - a2 * out
        return out
    }

    private fun set(b0n: Float, b1n: Float, b2n: Float, a0: Float, a1n: Float, a2n: Float) {
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0; a1 = a1n / a0; a2 = a2n / a0
    }

    fun peaking(sr: Int, freq: Float, q: Float, gainDb: Float) {
        val A = 10f.pow(gainDb / 40f)
        val w = 2f * PI.toFloat() * freq / sr
        val cs = cos(w); val sn = sin(w); val alpha = sn / (2f * q)
        set(1 + alpha * A, -2 * cs, 1 - alpha * A, 1 + alpha / A, -2 * cs, 1 - alpha / A)
    }

    fun lowShelf(sr: Int, freq: Float, gainDb: Float, q: Float = 0.707f) {
        val A = 10f.pow(gainDb / 40f)
        val w = 2f * PI.toFloat() * freq / sr
        val cs = cos(w); val sn = sin(w); val alpha = sn / (2f * q)
        val beta = 2f * sqrt(A) * alpha
        set(A * ((A + 1) - (A - 1) * cs + beta), 2 * A * ((A - 1) - (A + 1) * cs),
            A * ((A + 1) - (A - 1) * cs - beta), (A + 1) + (A - 1) * cs + beta,
            -2 * ((A - 1) + (A + 1) * cs), (A + 1) + (A - 1) * cs - beta)
    }

    fun highShelf(sr: Int, freq: Float, gainDb: Float, q: Float = 0.707f) {
        val A = 10f.pow(gainDb / 40f)
        val w = 2f * PI.toFloat() * freq / sr
        val cs = cos(w); val sn = sin(w); val alpha = sn / (2f * q)
        val beta = 2f * sqrt(A) * alpha
        set(A * ((A + 1) + (A - 1) * cs + beta), -2 * A * ((A - 1) + (A + 1) * cs),
            A * ((A + 1) + (A - 1) * cs - beta), (A + 1) - (A - 1) * cs + beta,
            2 * ((A - 1) - (A + 1) * cs), (A + 1) - (A - 1) * cs - beta)
    }

    fun highpass(sr: Int, freq: Float, q: Float = 0.707f) {
        val w = 2f * PI.toFloat() * freq / sr
        val cs = cos(w); val sn = sin(w); val alpha = sn / (2f * q)
        set((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + alpha, -2 * cs, 1 - alpha)
    }

    fun lowpass(sr: Int, freq: Float, q: Float = 0.707f) {
        val w = 2f * PI.toFloat() * freq / sr
        val cs = cos(w); val sn = sin(w); val alpha = sn / (2f * q)
        set((1 - cs) / 2, 1 - cs, (1 - cs) / 2, 1 + alpha, -2 * cs, 1 - alpha)
    }
}
