package host.dh.app.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real RIFF/WAVE writer for PCM 16-bit audio.
 *
 * Strategy: write a 44-byte placeholder header immediately, stream raw PCM
 * frames, then seek back on [finish] and patch the RIFF/data chunk sizes so the
 * file is always a valid, playable WAV.
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int,
    private val bitsPerSample: Int = 16,
) {
    private var raf: RandomAccessFile? = null
    private var dataBytes: Long = 0

    fun open() {
        file.parentFile?.mkdirs()
        val r = RandomAccessFile(file, "rw")
        r.setLength(0)
        r.write(header(0))
        raf = r
        dataBytes = 0
    }

    /** Append PCM 16-bit little-endian samples. */
    fun writeShorts(buf: ShortArray, count: Int) {
        val r = raf ?: return
        val bytes = ByteArray(count * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) bb.putShort(buf[i])
        r.write(bytes)
        dataBytes += bytes.size
    }

    fun writeBytes(bytes: ByteArray, count: Int) {
        val r = raf ?: return
        r.write(bytes, 0, count)
        dataBytes += count
    }

    fun finish(): File {
        val r = raf ?: return file
        r.seek(0)
        r.write(header(dataBytes))
        r.fd.sync()
        r.close()
        raf = null
        return file
    }

    fun abort() {
        try { raf?.close() } catch (_: Exception) {}
        raf = null
    }

    private fun header(dataLen: Long): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffLen = 36 + dataLen
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(riffLen.toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)                       // PCM fmt chunk size
        bb.putShort(1)                      // PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(blockAlign.toShort())
        bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataLen.toInt())
        return bb.array()
    }
}

/** Parsed WAV header + validation used by the DSP renderer and tests. */
data class WavInfo(
    val valid: Boolean,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataBytes: Long,
    val reason: String = "",
) {
    val durationMs: Long
        get() = if (sampleRate > 0 && channels > 0)
            (dataBytes * 1000L) / (sampleRate.toLong() * channels * (bitsPerSample / 8)) else 0
}

object WavIo {
    fun readInfo(file: File): WavInfo {
        if (!file.exists() || file.length() < 44)
            return WavInfo(false, 0, 0, 0, 0, 0, "too short")
        RandomAccessFile(file, "r").use { r ->
            val head = ByteArray(12)
            r.readFully(head)
            if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                String(head, 8, 4, Charsets.US_ASCII) != "WAVE")
                return WavInfo(false, 0, 0, 0, 0, 0, "missing RIFF/WAVE")
            var sampleRate = 0; var channels = 0; var bits = 0
            var dataOffset = 0L; var dataLen = 0L
            val chunk = ByteArray(8)
            while (r.filePointer + 8 <= r.length()) {
                r.readFully(chunk)
                val id = String(chunk, 0, 4, Charsets.US_ASCII)
                val sz = leInt(chunk, 4).toLong() and 0xFFFFFFFFL
                if (id == "fmt ") {
                    val fmt = ByteArray(sz.toInt())
                    r.readFully(fmt)
                    channels = leShort(fmt, 2)
                    sampleRate = leInt(fmt, 4)
                    bits = leShort(fmt, 14)
                } else if (id == "data") {
                    dataOffset = r.filePointer
                    dataLen = minOf(sz, r.length() - dataOffset)
                    break
                } else {
                    r.seek(r.filePointer + sz + (sz and 1)) // chunks are word-aligned
                }
            }
            val ok = sampleRate > 0 && channels in 1..2 && bits == 16 && dataOffset > 0
            return WavInfo(ok, sampleRate, channels, bits, dataOffset, dataLen,
                if (ok) "" else "unsupported fmt")
        }
    }

    private fun leInt(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun leShort(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
}
