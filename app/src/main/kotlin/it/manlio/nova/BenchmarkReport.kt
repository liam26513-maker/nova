package it.manlio.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.device.DeviceProfile
import kotlin.math.roundToInt

/** Resoconto della misura delle prestazioni: fluidità di ogni livello e scelta fatta. */
@Composable
fun BenchmarkReportScreen(
    profile: DeviceProfile,
    onDone: () -> Unit,
) {
    val result = profile.benchmark ?: return
    val t = Nova.tokens
    val ceiling = stringResource(profile.ceiling.label())
    val start = stringResource(profile.startLevel.label())
    SettingsScaffold(
        title = stringResource(R.string.report_title),
        subtitle =
            if (profile.ceiling == profile.startLevel) {
                stringResource(R.string.report_verdict, ceiling)
            } else {
                stringResource(R.string.report_verdict_start, ceiling, start)
            },
    ) { backdrop ->
        SettingsSection(
            backdrop,
            header = stringResource(R.string.report_levels),
            footer = stringResource(R.string.report_footer),
        ) {
            GraphicsLevel.entries.forEachIndexed { i, level ->
                val smooth = result.smoothPercent(level)
                val p90 = result.p90MsByLevel[level] ?: 0f
                SettingsBlock(showDivider = i > 0) {
                    Column {
                        val title =
                            stringResource(level.label()) +
                                if (level == profile.ceiling) " · " + stringResource(R.string.report_recommended) else ""
                        val weight = if (level == profile.ceiling) FontWeight.SemiBold else FontWeight.Normal
                        BasicText(title, style = TextStyle(t.colors.onSurface, 17.sp, weight))
                        Spacer(Modifier.height(NovaSpacing.xxs))
                        BasicText(
                            stringResource(R.string.report_level_value, smooth.roundToInt(), p90.roundToInt()),
                            style = TextStyle(t.colors.onSurfaceMuted, 14.sp),
                        )
                        Spacer(Modifier.height(NovaSpacing.xs))
                        SmoothBar(smooth / FULL)
                    }
                }
            }
        }
        SettingsSection(backdrop) {
            SettingsRow(stringResource(R.string.report_done), summary = stringResource(R.string.report_done_summary), onClick = onDone)
        }
    }
}

/** Barra sottile: quanto del tempo il livello è rimasto fluido. */
@Composable
private fun SmoothBar(fraction: Float) {
    val t = Nova.tokens
    val shape = SquircleShape(BAR_HEIGHT / 2)
    Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(t.colors.onSurface.copy(alpha = 0.08f), shape)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(t.colors.accent, shape))
    }
}

private const val FULL = 100f
private val BAR_HEIGHT = 6.dp
