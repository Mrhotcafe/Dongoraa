package host.dh.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import host.dh.app.audio.WavIo
import java.io.File

data class Recording(
    val file: File,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val valid: Boolean,
)

object Recordings {
    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "Recordings").apply { mkdirs() }
    fun processedDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "Processed").apply { mkdirs() }

    fun list(ctx: Context): List<Recording> {
        val files = dir(ctx).listFiles { f -> f.isFile && f.extension.equals("wav", true) } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { toRecording(it) }
    }

    fun toRecording(f: File): Recording {
        val info = WavIo.readInfo(f)
        return Recording(
            file = f,
            name = f.nameWithoutExtension,
            durationMs = info.durationMs,
            sizeBytes = f.length(),
            dateMs = f.lastModified(),
            sampleRate = info.sampleRate,
            channels = info.channels,
            valid = info.valid,
        )
    }

    fun rename(rec: Recording, newName: String): Recording? {
        val safe = newName.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { return null }
        val target = File(rec.file.parentFile, "$safe.wav")
        if (target.exists()) return null
        return if (rec.file.renameTo(target)) toRecording(target) else null
    }

    fun delete(rec: Recording): Boolean = rec.file.delete()

    fun share(ctx: Context, rec: Recording) {
        val uri = FileProvider.getUriForFile(ctx, "host.dh.app.fileprovider", rec.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share recording").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
