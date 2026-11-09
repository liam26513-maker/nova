package it.manlio.nova

import android.content.Context
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Quanto scatta la Home in ogni versione (SPEC §4.8, confronto prima/dopo nelle Novità). Conta i fotogrammi già misurati per il
 * livello grafico: un fotogramma è lento se supera [SLOW_NS]. Si tengono solo la versione attuale e la precedente; niente esce.
 */
internal class HomeFluency(
    context: Context,
    private val version: Int,
) {
    private val prefs = context.getSharedPreferences("fluidita", Context.MODE_PRIVATE)
    private val frames = AtomicInteger()
    private val slow = AtomicInteger()

    /** Dal thread dei fotogrammi: solo due contatori, niente allocazioni. */
    fun frame(totalNs: Long) {
        frames.incrementAndGet()
        if (totalNs > SLOW_NS) slow.incrementAndGet()
    }

    /** Uscendo dalla Home: somma a quanto già contato per questa versione e, al cambio di versione, tiene la precedente. */
    fun save() {
        val f = frames.getAndSet(0)
        val s = slow.getAndSet(0)
        if (f == 0) return
        val edit = prefs.edit()
        if (prefs.getInt(CURRENT, version) != version) {
            edit.putInt(PREV_FRAMES, prefs.getInt(FRAMES, 0)).putInt(PREV_SLOW, prefs.getInt(SLOW, 0)).putInt(FRAMES, 0).putInt(SLOW, 0)
        }
        val base = if (prefs.getInt(CURRENT, version) == version) prefs.getInt(FRAMES, 0) to prefs.getInt(SLOW, 0) else 0 to 0
        edit.putInt(CURRENT, version).putInt(FRAMES, base.first + f).putInt(SLOW, base.second + s).apply()
    }

    /** La riga per le Novità, o null se non ci sono ancora abbastanza fotogrammi. */
    fun summary(context: Context): String? =
        FluencyStats.line(
            context,
            prefs.getInt(FRAMES, 0),
            prefs.getInt(SLOW, 0),
            prefs.getInt(PREV_FRAMES, 0),
            prefs.getInt(PREV_SLOW, 0),
        )

    private companion object {
        const val SLOW_NS = 25_000_000L // un fotogramma e mezzo a 60 Hz
        const val CURRENT = "version"
        const val FRAMES = "frames"
        const val SLOW = "slow"
        const val PREV_FRAMES = "prev_frames"
        const val PREV_SLOW = "prev_slow"
    }
}

/** Il confronto in parole semplici. Puro tranne le stringhe. */
internal object FluencyStats {
    const val MIN_FRAMES = 300
    private const val PERCENT = 100f

    fun percent(
        frames: Int,
        slow: Int,
    ): Float? = if (frames < MIN_FRAMES) null else slow * PERCENT / frames

    fun line(
        context: Context,
        frames: Int,
        slow: Int,
        prevFrames: Int,
        prevSlow: Int,
    ): String? {
        val now = percent(frames, slow) ?: return null
        val before = percent(prevFrames, prevSlow)
        val fmt = { p: Float -> String.format(Locale.ITALIAN, "%.1f%%", p) }
        return if (before == null) {
            context.getString(R.string.news_fluency, fmt(now))
        } else {
            context.getString(R.string.news_fluency_compare, fmt(now), fmt(before))
        }
    }
}
