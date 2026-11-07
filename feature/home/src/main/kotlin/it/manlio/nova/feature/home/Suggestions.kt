package it.manlio.nova.feature.home

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp

/**
 * I suggerimenti locali (SPEC §12.5): quante volte un'app è stata aperta, contando di più le aperture recenti e quelle alla stessa
 * ora del giorno. Modello semplice e trasparente, calcolato sul tablet; niente esce. Pura, senza Android.
 */
object Suggestions {
    private const val HALF_LIFE_DAYS = 7.0
    private const val SAME_HOUR_BONUS = 2.0
    private const val MS_PER_DAY = 86_400_000.0
    private const val HOURS = 24
    const val COUNT = 5

    /** [opens]: pacchetto e momento di ogni apertura. Restituisce i pacchetti, dal più probabile. */
    fun rank(
        opens: List<Pair<String, Long>>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        skip: Set<String> = emptySet(),
    ): List<String> {
        val hourNow = Instant.ofEpochMilli(now).atZone(zone).hour
        return opens
            .filter { (pkg, time) -> pkg !in skip && time <= now }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.sumOf { weight(it, now, hourNow, zone) } }
            .entries
            .sortedByDescending { it.value }
            .take(COUNT)
            .map { it.key }
    }

    private fun weight(
        time: Long,
        now: Long,
        hourNow: Int,
        zone: ZoneId,
    ): Double {
        val ageDays = (now - time) / MS_PER_DAY
        val hour = Instant.ofEpochMilli(time).atZone(zone).hour
        val distance = abs(hour - hourNow).let { minOf(it, HOURS - it) }
        val sameHour = if (distance <= 1) SAME_HOUR_BONUS else 1.0
        return exp(-ageDays * Math.log(2.0) / HALF_LIFE_DAYS) * sameHour
    }
}
