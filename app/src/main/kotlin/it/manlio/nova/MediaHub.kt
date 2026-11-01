package it.manlio.nova

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.asImageBitmap
import it.manlio.nova.feature.home.MediaSource
import it.manlio.nova.feature.home.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La musica in riproduzione per l'isola e il widget (SPEC §6.5, §6.7), dalle sessioni multimediali di Android.
 * Serve l'accesso alle notifiche (lo stesso del servizio di NOVA). Ascolta solo tra [start] e [stop], cioè mentre
 * la Home è visibile; ogni chiamata ad Android è protetta (senza accesso, semplicemente non c'è musica).
 */
class MediaHub(private val context: Context) : MediaSource {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, NovaNotificationListener::class.java)
    private val nowState = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = nowState.asStateFlow()
    private var current: MediaController? = null
    private var watched: List<MediaController> = emptyList()
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { update(it.orEmpty()) }
    private val callback =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()

            override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        }

    fun start() {
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessions, listener)
            update(manager.getActiveSessions(listener))
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        watched.forEach { runCatching { it.unregisterCallback(callback) } }
        watched = emptyList()
        current = null
        nowState.value = null
    }

    private fun update(controllers: List<MediaController>) {
        watched.forEach { runCatching { it.unregisterCallback(callback) } }
        watched = controllers
        controllers.forEach { runCatching { it.registerCallback(callback) } }
        // Prima chi sta suonando, altrimenti la sessione più recente.
        current = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
        refresh()
    }

    private fun refresh() {
        val c = current ?: return run { nowState.value = null }
        val meta = c.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) return run { nowState.value = null }
        val art = (meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.let(::small)
        nowState.value =
            NowPlaying(
                pkg = c.packageName,
                title = title,
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                art = art?.asImageBitmap(),
            )
    }

    /** La copertina si tiene piccola: serve a 56 dp. */
    private fun small(b: Bitmap): Bitmap =
        if (b.width <= ART_PX) b else Bitmap.createScaledBitmap(b, ART_PX, ART_PX * b.height / b.width, true)

    override fun previous() = control { transportControls.skipToPrevious() }

    override fun next() = control { transportControls.skipToNext() }

    override fun toggle() =
        control {
            if (playbackState?.state == PlaybackState.STATE_PLAYING) transportControls.pause() else transportControls.play()
        }

    override fun open() = control { sessionActivity?.send() }

    private fun control(action: MediaController.() -> Unit) {
        current?.let { runCatching { it.action() } }
    }

    private companion object {
        const val ART_PX = 256
    }
}
