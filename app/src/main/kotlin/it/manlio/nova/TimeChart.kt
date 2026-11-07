package it.manlio.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import java.time.LocalDate
import java.time.format.TextStyle.SHORT
import java.util.Locale

private const val WEEK_DAYS = 7
private const val FADED = 0.45f
private val BAR_HEIGHT = 96.dp
private val BAR_WIDTH = 24.dp
private val MIN_BAR = 2.dp

/** Un giorno del grafico: quanti minuti e quanto è alta la barra, da 0 a 1 rispetto al giorno più pieno. */
internal data class DayBar(
    val day: LocalDate,
    val minutes: Int,
    val height: Float,
)

/** Gli ultimi 7 giorni, dal più vecchio a [today]. */
internal fun weekBars(
    today: LocalDate,
    minutesOn: (LocalDate) -> Int,
): List<DayBar> {
    val days = (WEEK_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to minutesOn(it) }
    val most = days.maxOf { it.second }.coerceAtLeast(1)
    return days.map { (day, minutes) -> DayBar(day, minutes, minutes.toFloat() / most) }
}

/** I minuti nelle modalità, giorno per giorno. Toccando una barra il suo valore compare in alto; all'inizio è quello di oggi. */
@Composable
internal fun WeekChart(bars: List<DayBar>) {
    val colors = Nova.tokens.colors
    var chosen by remember(bars) { mutableIntStateOf(bars.lastIndex) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            "${dayName(bars[chosen].day)}: ${readableTime(bars[chosen].minutes)}",
            style = TextStyle(colors.onSurface, 16.sp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            bars.forEachIndexed { index, bar ->
                val label = "${dayName(bar.day)}: ${readableTime(bar.minutes)}"
                Column(
                    Modifier
                        .semantics(mergeDescendants = true) {
                            contentDescription = label
                            selected = index == chosen
                        }.clickable { chosen = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.height(BAR_HEIGHT).width(BAR_WIDTH + 16.dp), contentAlignment = Alignment.BottomCenter) {
                        if (bar.minutes > 0) {
                            Box(
                                Modifier
                                    .width(BAR_WIDTH)
                                    .height(maxOf(BAR_HEIGHT * bar.height, MIN_BAR))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(colors.accent.copy(alpha = if (index == chosen) 1f else FADED)),
                            )
                        }
                    }
                    BasicText(dayName(bar.day), style = TextStyle(colors.onSurfaceMuted, 13.sp))
                }
            }
        }
    }
}

private fun dayName(day: LocalDate) = day.dayOfWeek.getDisplayName(SHORT, Locale.getDefault())
