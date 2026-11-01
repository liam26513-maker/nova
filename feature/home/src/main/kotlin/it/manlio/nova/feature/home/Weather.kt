package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Che tempo fa, in forma semplice. [kind] riassume il codice WMO di Open-Meteo. */
@Immutable
data class Weather(
    val city: String,
    val tempC: Int,
    val hiC: Int,
    val loC: Int,
    val kind: WeatherKind,
    val isDay: Boolean,
)

enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Un luogo trovato dalla ricerca per nome. */
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Lettura delle risposte di Open-Meteo (gratuito, senza chiave): ricerca del luogo per nome e previsione.
 * Pura e provata con test; la rete sta nell'app (`WeatherClient`).
 */
object WeatherParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Codici WMO: https://open-meteo.com/en/docs (tabella "WMO Weather interpretation codes"). */
    @Suppress("MagicNumber") // codici standard WMO
    fun kindOf(code: Int): WeatherKind =
        when (code) {
            0 -> WeatherKind.CLEAR
            1, 2 -> WeatherKind.PARTLY
            3 -> WeatherKind.CLOUDY
            45, 48 -> WeatherKind.FOG
            in 51..57 -> WeatherKind.DRIZZLE
            in 61..67, in 80..82 -> WeatherKind.RAIN
            in 71..77, 85, 86 -> WeatherKind.SNOW
            in 95..99 -> WeatherKind.STORM
            else -> WeatherKind.CLOUDY
        }

    fun parsePlace(text: String): Place? =
        runCatching {
            val first = json.parseToJsonElement(text).jsonObject["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            Place(
                name = first.string("name") ?: return null,
                latitude = first.double("latitude") ?: return null,
                longitude = first.double("longitude") ?: return null,
            )
        }.getOrNull()

    fun parseForecast(
        text: String,
        city: String,
    ): Weather? =
        runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val now = root["current"]?.jsonObject ?: return null
            val daily = root["daily"]?.jsonObject
            val temp = now.double("temperature_2m") ?: return null
            Weather(
                city = city,
                tempC = Math.round(temp).toInt(),
                hiC = daily.firstOf("temperature_2m_max")?.let { Math.round(it).toInt() } ?: Math.round(temp).toInt(),
                loC = daily.firstOf("temperature_2m_min")?.let { Math.round(it).toInt() } ?: Math.round(temp).toInt(),
                kind = kindOf(now["weather_code"]?.jsonPrimitive?.intOrNull ?: return null),
                isDay = (now["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1,
            )
        }.getOrNull()

    fun placeUrl(city: String) = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(city)}&count=1&language=it&format=json"

    fun forecastUrl(place: Place) =
        "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

    private fun enc(s: String) = java.net.URLEncoder.encode(s.trim(), "UTF-8")

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.double(key: String) = this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject?.firstOf(key: String) = this?.get(key)?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
}
