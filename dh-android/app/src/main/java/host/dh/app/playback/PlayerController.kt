package host.dh.app.playback

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlayState(
    val path: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/** Simple MediaPlayer wrapper with seek + a ticking position for the UI. */
object PlayerController {
    val state = MutableStateFlow(PlayState())
    private var mp: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun toggle(file: File) {
        val cur = state.value
        if (cur.path == file.absolutePath && mp != null) {
            if (cur.playing) pause() else resume()
        } else {
            play(file)
        }
    }

    fun play(file: File) {
        stop()
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.setOnCompletionListener {
                state.value = state.value.copy(playing = false, positionMs = 0)
                ticker?.cancel()
            }
            player.start()
            mp = player
            state.value = PlayState(file.absolutePath, true, 0, player.duration)
            startTicker()
        } catch (e: Exception) {
            player.release()
            state.value = PlayState()
        }
    }

    fun pause() {
        mp?.let { if (it.isPlaying) it.pause() }
        state.value = state.value.copy(playing = false)
        ticker?.cancel()
    }

    fun resume() {
        mp?.let { it.start(); startTicker() }
        state.value = state.value.copy(playing = true)
    }

    fun seekTo(ms: Int) {
        mp?.seekTo(ms)
        state.value = state.value.copy(positionMs = ms)
    }

    fun stop() {
        ticker?.cancel()
        mp?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        mp = null
        state.value = PlayState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val p = mp ?: break
                if (p.isPlaying) state.value = state.value.copy(positionMs = p.currentPosition, playing = true)
                delay(200)
            }
        }
    }
}
