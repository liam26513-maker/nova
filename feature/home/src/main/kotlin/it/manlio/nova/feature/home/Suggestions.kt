package it.manlio.nova.feature.home

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp

/** Un'apertura di un'app: quando, e in che situazione (es. «cuffie», «tastiera», «mode:study»). */
data class Open(
    val pkg: String,
    val time: Long,
    val tags: Set<String> = emptySet(),
)

/** Perché un'app è suggerita: la usi spesso in questa situazione ([tag]), a quest'ora, o in generale. */
enum class Why { CONTEXT, THIS_HOUR, OFTEN }

data class Suggested(
    val pkg: String,
    val why: Why,
    val tag: String? = null,
)

/**
 * I suggerimenti locali (SPEC §12.5): quante volte un'app è stata aperta, contando di più le aperture recenti, quelle alla stessa ora
 * del giorno e quelle nella stessa situazione di adesso (accessori collegati, modalità accesa). Ogni suggerimento dice il perché.
 * Modello semplice e trasparente, calcolato sul tablet; niente esce. Pura, senza Android.
 */
object Suggestions {
    private const val HALF_LIFE_DAYS = 7.0
    private const val SAME_HOUR_BONUS = 2.0
    private const val CONTEXT_BONUS = 2.0
    private const val MAIN_REASON = 0.5
    private const val MS_PER_DAY = 86_400_000.0
    private const val HOURS = 24
    const val COUNT = 5

    /** Solo i pacchetti, dal più probabile (senza situazione). */
    fun rank(
        opens: List<Pair<String, Long>>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        skip: Set<String> = emptySet(),
    ): List<String> = suggest(opens.map { Open(it.first, it.second) }, now, zone, skip).map { it.pkg }

    fun suggest(
        opens: List<Open>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        skip: Set<String> = emptySet(),
        tagsNow: Set<String> = emptySet(),
    ): List<Suggested> {
        val hourNow = Instant.ofEpochMilli(now).atZone(zone).hour
        return opens
            .filter { it.pkg !in skip && it.time <= now }
            .groupBy { it.pkg }
            .map { (pkg, list) -> score(pkg, list, now, hourNow, zone, tagsNow) }
            .sortedByDescending { it.second }
            .take(COUNT)
            .map { it.first }
    }

    private fun score(
        pkg: String,
        list: List<Open>,
        now: Long,
        hourNow: Int,
        zone: ZoneId,
        tagsNow: Set<String>,
    ): Pair<Suggested, Double> {
        var total = 0.0
        var hourPart = 0.0
        val contextPart = mutableMapOf<String, Double>()
        list.forEach { open ->
            val ageDays = (now - open.time) / MS_PER_DAY
            val hour = Instant.ofEpochMilli(open.time).atZone(zone).hour
            val distance = abs(hour - hourNow).let { minOf(it, HOURS - it) }
            val sameHour = distance <= 1
            val shared = open.tags intersect tagsNow
            val w =
                exp(-ageDays * Math.log(2.0) / HALF_LIFE_DAYS) * (if (sameHour) SAME_HOUR_BONUS else 1.0) *
                    (if (shared.isNotEmpty()) CONTEXT_BONUS else 1.0)
            total += w
            if (sameHour) hourPart += w
            shared.forEach { contextPart[it] = (contextPart[it] ?: 0.0) + w }
        }
        val topTag = contextPart.maxByOrNull { it.value }
        val why =
            when {
                topTag != null && topTag.value >= total * MAIN_REASON -> Suggested(pkg, Why.CONTEXT, topTag.key)
                hourPart >= total * MAIN_REASON -> Suggested(pkg, Why.THIS_HOUR)
                else -> Suggested(pkg, Why.OFTEN)
            }
        return why to total
    }
}
