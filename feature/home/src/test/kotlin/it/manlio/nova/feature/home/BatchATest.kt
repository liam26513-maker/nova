package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherParserTest {
    @Test
    fun `wmo codes become simple kinds`() {
        assertEquals(WeatherKind.CLEAR, WeatherParser.kindOf(0))
        assertEquals(WeatherKind.PARTLY, WeatherParser.kindOf(2))
        assertEquals(WeatherKind.CLOUDY, WeatherParser.kindOf(3))
        assertEquals(WeatherKind.FOG, WeatherParser.kindOf(45))
        assertEquals(WeatherKind.DRIZZLE, WeatherParser.kindOf(53))
        assertEquals(WeatherKind.RAIN, WeatherParser.kindOf(63))
        assertEquals(WeatherKind.RAIN, WeatherParser.kindOf(81))
        assertEquals(WeatherKind.SNOW, WeatherParser.kindOf(73))
        assertEquals(WeatherKind.SNOW, WeatherParser.kindOf(86))
        assertEquals(WeatherKind.STORM, WeatherParser.kindOf(96))
        assertEquals(WeatherKind.CLOUDY, WeatherParser.kindOf(1234)) // codice sconosciuto: nuvoloso
    }

    @Test
    fun `a place is read from the geocoding answer`() {
        val json =
            """{"results":[{"id":1,"name":"Roma","latitude":41.89193,"longitude":12.51133,"country":"Italia"}],"generationtime_ms":0.5}"""
        assertEquals(Place("Roma", 41.89193, 12.51133), WeatherParser.parsePlace(json))
        assertNull(WeatherParser.parsePlace("""{"generationtime_ms":0.2}""")) // nessun risultato
        assertNull(WeatherParser.parsePlace("non è json"))
        assertNull(WeatherParser.parsePlace("""{"results":[{"name":"X"}]}""")) // senza coordinate
    }

    @Test
    fun `the forecast is read and temperatures are rounded`() {
        val json =
            """{"current":{"temperature_2m":18.6,"weather_code":61,"is_day":0},
               "daily":{"temperature_2m_max":[21.4],"temperature_2m_min":[12.5]},"latitude":41.9}"""
        val w = WeatherParser.parseForecast(json, "Roma")!!
        assertEquals(Weather("Roma", 19, 21, 13, WeatherKind.RAIN, isDay = false), w)
    }

    @Test
    fun `a forecast without daily data still works and a broken one is refused`() {
        val w = WeatherParser.parseForecast("""{"current":{"temperature_2m":5.0,"weather_code":0,"is_day":1}}""", "Aosta")!!
        assertEquals(5, w.hiC)
        assertEquals(5, w.loC)
        assertNull(WeatherParser.parseForecast("""{"current":{"weather_code":0}}""", "X"))
        assertNull(WeatherParser.parseForecast("{}", "X"))
    }

    @Test
    fun `urls encode the city and carry no personal data`() {
        assertTrue(WeatherParser.placeUrl("San Marino").contains("name=San+Marino"))
        val url = WeatherParser.forecastUrl(Place("Roma", 41.9, 12.5))
        assertTrue(url.startsWith("https://api.open-meteo.com/"))
        assertTrue(url.contains("latitude=41.9") && url.contains("longitude=12.5"))
    }
}

class PomodoroTest {
    @Test
    fun `starts, counts down, pauses and resumes`() {
        val t0 = 1_000_000L
        val p = Pomodoro().start(t0)
        assertEquals(Pomodoro.FOCUS_MS - 60_000, p.remaining(t0 + 60_000))
        val paused = p.pause(t0 + 60_000)
        assertEquals(false, paused.running)
        assertEquals(Pomodoro.FOCUS_MS - 60_000, paused.remaining(t0 + 999_999)) // in pausa il tempo non passa
        val resumed = paused.start(t0 + 500_000)
        assertEquals(Pomodoro.FOCUS_MS - 60_000 - 1_000, resumed.remaining(t0 + 501_000))
    }

    @Test
    fun `at the end it moves to the other phase, stopped and ready`() {
        val p = Pomodoro().start(0)
        assertEquals(p, p.tick(Pomodoro.FOCUS_MS - 1)) // non ancora
        val brk = p.tick(Pomodoro.FOCUS_MS)
        assertEquals(Pomodoro.Phase.BREAK, brk.phase)
        assertEquals(false, brk.running)
        assertEquals(Pomodoro.BREAK_MS, brk.remaining(0))
        assertEquals(Pomodoro.Phase.FOCUS, brk.start(0).tick(Pomodoro.BREAK_MS).phase)
    }

    @Test
    fun `progress goes from 0 to 1 and reset starts over`() {
        val p = Pomodoro().start(0)
        assertEquals(0f, p.progress(0), 0.001f)
        assertEquals(0.5f, p.progress(Pomodoro.FOCUS_MS / 2), 0.001f)
        assertEquals(Pomodoro(), p.reset())
    }

    @Test
    fun `it survives being saved as text, and bad text gives a fresh timer`() {
        val p = Pomodoro().start(123).pause(60_123)
        assertEquals(p, Pomodoro.decode(p.encode()))
        val running = Pomodoro(Pomodoro.Phase.BREAK, true, 999, 0)
        assertEquals(running, Pomodoro.decode(running.encode()))
        assertEquals(Pomodoro(), Pomodoro.decode(""))
        assertEquals(Pomodoro(), Pomodoro.decode("A|B|C|D"))
    }

    @Test
    fun `the clock shows minutes and seconds, rounding up`() {
        assertEquals("25:00", Pomodoro.clock(Pomodoro.FOCUS_MS))
        assertEquals("04:59", Pomodoro.clock(299_000))
        assertEquals("00:01", Pomodoro.clock(1))
        assertEquals("00:00", Pomodoro.clock(0))
    }
}

class CalcTest {
    @Test
    fun `arithmetic follows the usual precedence`() {
        assertEquals("84", Calc.answer("12*(3+4)"))
        assertEquals("14", Calc.answer("2+3*4"))
        assertEquals("20", Calc.answer("(2+3)*4"))
        assertEquals("512", Calc.answer("2^3^2")) // potenza associativa a destra: 2^(3^2)
        assertEquals("-5", Calc.answer("-2-3"))
        assertEquals("0.5", Calc.answer("1/2"))
        assertEquals("3.333333", Calc.answer("10/3"))
        assertEquals("2.5", Calc.answer("5,0/2")) // la virgola vale come punto
        assertEquals("6", Calc.answer("2 × 3"))
    }

    @Test
    fun `percentages`() {
        assertEquals("12", Calc.answer("15% di 80"))
        assertEquals("0.5", Calc.answer("50%"))
    }

    @Test
    fun `things that are not calculations give nothing`() {
        listOf("", "chrome", "42", "3.5", "2+", "(1+2", "1/0x", "abc+1", "12 34").forEach { assertNull(it, Calc.answer(it)) }
        assertNull(Calc.answer("1/0")) // infinito: nessuna risposta
    }

    @Test
    fun `units convert within a group only`() {
        assertEquals("6.213712 mi", Calc.answer("10 km in mi"))
        assertEquals("100 cm", Calc.answer("1 m a cm"))
        assertEquals("2.204623 lb", Calc.answer("1 kg in lb"))
        assertEquals("212 f", Calc.answer("100 c in f"))
        assertEquals("273.15 k", Calc.answer("0°c in k"))
        assertNull(Calc.answer("10 km in kg")) // gruppi diversi
        assertNull(Calc.answer("10 foo in km"))
    }
}
