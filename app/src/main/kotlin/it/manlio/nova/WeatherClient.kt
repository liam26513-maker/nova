package it.manlio.nova

import it.manlio.nova.feature.home.Weather
import it.manlio.nova.feature.home.WeatherParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tempo di una città da Open-Meteo (gratuito, nessuna chiave, nessun account). Viaggia solo il nome della città
 * e, dopo la ricerca, le sue coordinate: niente identificativi, niente posizione del tablet.
 * La risposta si tiene in memoria per [TTL_MS]: tornare sulla Home non rifà la richiesta.
 * shortcut: la cache sparisce al riavvio dell'app, se serve più in là si salva su file.
 */
class WeatherClient {
    private val cache = HashMap<String, Pair<Long, Weather>>()

    suspend fun load(city: String): Result<Weather> =
        withContext(Dispatchers.IO) {
            val key = city.trim().lowercase()
            val hit = synchronized(cache) { cache[key] }
            if (hit != null && System.currentTimeMillis() - hit.first < TTL_MS) return@withContext Result.success(hit.second)
            runCatching {
                val place = WeatherParser.parsePlace(get(WeatherParser.placeUrl(city))) ?: throw IOException("Città non trovata: $city")
                WeatherParser.parseForecast(get(WeatherParser.forecastUrl(place)), place.name) ?: throw IOException("Risposta non valida")
            }.onSuccess { w -> synchronized(cache) { cache[key] = System.currentTimeMillis() to w } }
                // Senza rete si mostra l'ultimo tempo conosciuto, anche se vecchio, meglio di niente.
                .recoverCatching { e -> hit?.second ?: throw e }
        }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Meteo: HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TTL_MS = 30 * 60_000L
        const val TIMEOUT_MS = 10_000
    }
}
