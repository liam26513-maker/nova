package it.manlio.nova

import android.content.Context
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection

/** Una voce del CHANGELOG: il titolo e le righe che la raccontano. */
internal data class NewsEntry(
    val title: String,
    val lines: List<String>,
)

/** Legge le voci («### ») della sezione «Non rilasciato» del CHANGELOG, la più recente prima. Pura. */
internal object ChangelogParser {
    fun entries(text: String): List<NewsEntry> {
        val section = text.substringAfter("## Non rilasciato", "").substringBefore("\n## ")
        return section.split("\n### ").drop(1).map { block ->
            val lines = block.lines()
            NewsEntry(lines.first().trim(), lines.drop(1).map { it.trim().removePrefix("- ") }.filter { it.isNotEmpty() })
        }
    }
}

/**
 * Le novità (SPEC §4.8): il CHANGELOG viaggia dentro l'app e si ricordano i titoli già visti. Alla prima apertura in assoluto si
 * segnano tutti come visti, così non arriva un elenco infinito.
 */
internal class NewsStore(context: Context) {
    private val prefs = context.getSharedPreferences("novita", Context.MODE_PRIVATE)
    val entries: List<NewsEntry> =
        runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }
            .map(ChangelogParser::entries)
            .getOrDefault(emptyList())

    private val seen: Set<String> =
        prefs.getStringSet(SEEN, null) ?: entries.map { it.title }.toSet().also { prefs.edit().putStringSet(SEEN, it).apply() }

    val fresh: List<NewsEntry> get() = entries.filter { it.title !in seen }

    /** Dopo un aggiornamento, una volta per versione: quante novità ci sono da leggere (null = niente da dire). */
    fun announce(versionCode: Int): Int? {
        if (prefs.getInt(ANNOUNCED, 0) == versionCode) return null
        prefs.edit().putInt(ANNOUNCED, versionCode).apply()
        return fresh.size.takeIf { it > 0 }
    }

    fun markSeen() = prefs.edit().putStringSet(SEEN, entries.map { it.title }.toSet()).apply()

    private companion object {
        const val SEEN = "seen"
        const val ANNOUNCED = "announced"
    }
}

private const val SHOWN = 40

/** Impostazioni → Novità: le ultime voci, in italiano semplice, con la richiesta originale. Uscendo diventano «viste». */
@Composable
internal fun NewsSection(backdrop: ImageBitmap) {
    val context = LocalContext.current
    val store = remember { NewsStore(context) }
    val fresh = remember { store.fresh.map { it.title }.toSet() }
    DisposableEffect(Unit) { onDispose { store.markSeen() } }
    val fluency = remember { HomeFluency(context, BuildConfig.VERSION_CODE).summary(context) }
    fluency?.let { SettingsSection(backdrop, header = stringResource(R.string.news_fluency_title)) { SettingsRow(it) } }
    SettingsSection(backdrop, header = stringResource(R.string.news_recent), footer = stringResource(R.string.news_footer)) {
        if (store.entries.isEmpty()) SettingsRow(stringResource(R.string.news_none))
        store.entries.take(SHOWN).forEachIndexed { index, entry ->
            SettingsRow(
                entry.title,
                summary = entry.lines.joinToString("\n"),
                showDivider = index > 0,
                trailing =
                    if (entry.title in fresh) {
                        { BasicText(stringResource(R.string.news_new), style = TextStyle(Nova.tokens.colors.accent, 14.sp)) }
                    } else {
                        null
                    },
            )
        }
    }
}
