package it.manlio.nova

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsPreview
import it.manlio.nova.core.design.GraphicsState
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SegmentedChoice
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.design.SidebarEntry
import it.manlio.nova.core.design.SidebarGlyph
import it.manlio.nova.core.design.SplitSettings
import it.manlio.nova.core.device.DeviceProfile
import kotlin.math.roundToInt

/**
 * Pagina Impostazioni (provvisoria finché non arriva la home, Fase 0 punto 11).
 * Usa il linguaggio visivo calmo comune a tutte le schermate di sistema.
 */
@Composable
fun SettingsScreen(
    brandName: String,
    configVersion: String,
    graphics: GraphicsState,
    profile: DeviceProfile?,
    onChoose: (LevelChoice) -> Unit,
    onMeasure: () -> Unit,
    onShowReport: () -> Unit,
    system: SystemUiState,
    systemActions: SystemActions,
    performance: PerformanceUi? = null,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    SplitSettings(
        title = stringResource(R.string.settings_title),
        subtitle = brandName,
        entries = settingsEntries(performance != null),
        selectedId = selected,
        onSelect = { selected = it },
    ) { entry, backdrop ->
        when (entry.id) {
            CAT_NEWS -> NewsSection(backdrop)
            CAT_PEN -> PenSection(backdrop)
            CAT_IMPROVE -> ImproveSection(backdrop, systemActions)
            CAT_GRAPHICS -> GraphicsSection(backdrop, graphics, onChoose)
            CAT_DEVICE -> DeviceSection(backdrop, profile, onMeasure, onShowReport)
            CAT_HOME -> HomeSection(backdrop, system, systemActions)
            CAT_UPDATES -> UpdatesSection(backdrop, system, systemActions)
            CAT_PERFORMANCE -> performance?.let { PerformanceSection(backdrop, it, systemActions.onShizukuAction) }
            CAT_PRIVACY, CAT_GAMING, CAT_STUDY, CAT_PRESENTATION, CAT_TIME -> performance?.let { ModeCategory(entry.id, backdrop, it) }
            CAT_TOOLS -> SystemToolsSection(backdrop, system, systemActions)
            else -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val health = remember(system) { SystemStatusReader.read(context) }
                HealthSection(backdrop, health)
                SettingsSection(backdrop, header = stringResource(R.string.status_title)) {
                    SettingsRow(stringResource(R.string.status_config_title), trailing = { Value(configVersion) })
                    SettingsRow(
                        stringResource(R.string.status_modules_title),
                        showDivider = true,
                        trailing = { Value(stringResource(R.string.status_modules_none)) },
                    )
                }
            }
        }
    }
}

/** Le categorie della barra laterale, nell'ordine in cui compaiono. */
@Suppress("MagicNumber") // tavolozza delle pastiglie
@Composable
private fun settingsEntries(withPerformance: Boolean): List<SidebarEntry> {
    val improve = stringResource(R.string.improve_section)
    val context = LocalContext.current
    val fresh = remember { NewsStore(context).fresh.size }
    val news = if (fresh > 0) stringResource(R.string.news_title_count, fresh) else stringResource(R.string.news_title)
    val graphics = stringResource(R.string.graphics_level_title)
    val device = stringResource(R.string.device_title)
    val home = stringResource(R.string.home_section_title)
    val updates = stringResource(R.string.updates_title)
    val tools = stringResource(R.string.system_title)
    val performance = stringResource(R.string.perf_title)
    val gaming = stringResource(R.string.gaming_title)
    val status = stringResource(R.string.status_title)
    val perf =
        if (withPerformance) {
            listOf(
                SidebarEntry(CAT_PERFORMANCE, performance, SidebarGlyph.SPARKLE, Color(0xFFD6537A)),
            )
        } else {
            emptyList()
        }
    val study = stringResource(R.string.more_study)
    val presentation = stringResource(R.string.more_presentation)
    val time = stringResource(R.string.time_title)
    val privacy = stringResource(R.string.privacy_title)
    val pen = stringResource(R.string.pen_title)
    val game =
        if (withPerformance) {
            listOf(
                SidebarEntry(CAT_GAMING, gaming, SidebarGlyph.GRID, Color(0xFFE0603A)),
                SidebarEntry(CAT_STUDY, study, SidebarGlyph.TEXT, Color(0xFF4C9A6A)),
                SidebarEntry(CAT_PRESENTATION, presentation, SidebarGlyph.IMAGE, Color(0xFFB0793A)),
                SidebarEntry(CAT_TIME, time, SidebarGlyph.EYE, Color(0xFF5B7FD6)),
                SidebarEntry(CAT_PRIVACY, privacy, SidebarGlyph.DROP, Color(0xFF4A8C8C)),
            )
        } else {
            emptyList()
        }
    return remember(improve, graphics, device, home, updates, tools, status, performance, withPerformance, gaming) {
        listOf(
            SidebarEntry(CAT_NEWS, news, SidebarGlyph.DOWNLOAD, Color(0xFFE0603A)),
            SidebarEntry(CAT_IMPROVE, improve, SidebarGlyph.SPARKLE, Color(0xFF7B68D8)),
            SidebarEntry(CAT_GRAPHICS, graphics, SidebarGlyph.LAYERS, Color(0xFF2FA39A)),
            SidebarEntry(CAT_DEVICE, device, SidebarGlyph.TABLET, Color(0xFF6B7684)),
            SidebarEntry(CAT_PEN, pen, SidebarGlyph.DROP, Color(0xFF9A6BD8)),
            SidebarEntry(CAT_HOME, home, SidebarGlyph.HOUSE, Color(0xFFE0922F)),
            SidebarEntry(CAT_UPDATES, updates, SidebarGlyph.DOWNLOAD, Color(0xFF3B8BD4)),
        ) + perf + game +
            listOf(
                SidebarEntry(CAT_TOOLS, tools, SidebarGlyph.SLIDERS, Color(0xFF8A8F98)),
                SidebarEntry(CAT_STATUS, status, SidebarGlyph.CHECK, Color(0xFF3FA34D)),
            )
    }
}

/** Le pagine legate alle modalità, tutte possibili solo con gli strumenti di sistema. */
@Composable
private fun ModeCategory(
    id: String,
    backdrop: ImageBitmap,
    ui: PerformanceUi,
) {
    when (id) {
        CAT_GAMING -> {
            GameLibrarySection(backdrop, remember(ui) { ui.gameTools })
            GamingSection(backdrop, ui.modeOptions)
        }
        CAT_STUDY -> StudySection(backdrop, ui.modes)
        CAT_PRESENTATION -> PresentationSection(backdrop, ui.modes)
        CAT_PRIVACY -> PrivacySection(backdrop)
        else -> TimeSection(backdrop, ui.modes)
    }
}

private const val CAT_IMPROVE = "improve"
private const val CAT_NEWS = "news"
private const val CAT_PEN = "pen"
private const val CAT_GRAPHICS = "graphics"
private const val CAT_DEVICE = "device"
private const val CAT_HOME = "home"
private const val CAT_UPDATES = "updates"
private const val CAT_TOOLS = "tools"
private const val CAT_PERFORMANCE = "performance"
private const val CAT_GAMING = "gaming"
private const val CAT_STUDY = "study"
private const val CAT_PRESENTATION = "presentation"
private const val CAT_TIME = "time"
private const val CAT_PRIVACY = "privacy"
private const val CAT_STATUS = "status"

@Composable
private fun GraphicsSection(
    backdrop: ImageBitmap,
    graphics: GraphicsState,
    onChoose: (LevelChoice) -> Unit,
) {
    val t = Nova.tokens
    run {
        SettingsSection(
            backdrop,
            header = stringResource(R.string.graphics_level_title),
            footer = stringResource(R.string.graphics_level_footer),
        ) {
            SettingsBlock {
                SegmentedChoice(
                    options = LevelChoice.all,
                    selected = LevelChoice.of(graphics),
                    label = { stringResource(it.label()) },
                    onSelect = onChoose,
                )
                Spacer(Modifier.height(NovaSpacing.m))
                GraphicsPreview(graphics.level)
                Spacer(Modifier.height(NovaSpacing.s))
                BasicText(stringResource(graphics.level.explanation()), style = TextStyle(t.colors.onSurface, 15.sp, lineHeight = 21.sp))
                Spacer(Modifier.height(NovaSpacing.xxs))
                val current = stringResource(graphics.level.label())
                BasicText(
                    if (graphics.automatic) {
                        stringResource(R.string.graphics_level_auto_now, current)
                    } else {
                        stringResource(R.string.graphics_level_fixed_now, current)
                    },
                    style = TextStyle(t.colors.onSurfaceMuted, 14.sp),
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(
    backdrop: ImageBitmap,
    profile: DeviceProfile?,
    onMeasure: () -> Unit,
    onShowReport: () -> Unit,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.device_title),
        footer = stringResource(R.string.device_footer),
    ) {
        if (profile != null) {
            val cpu =
                listOf(profile.soc.ifBlank { profile.model }, stringResource(R.string.device_cores, profile.cpuCores))
                    .joinToString(" · ")
            SettingsRow(stringResource(R.string.device_cpu), trailing = { Value(cpu) })
            SettingsRow(
                stringResource(R.string.device_ram),
                showDivider = true,
                trailing = { Value(stringResource(R.string.device_gb, (profile.ramMb + MB_PER_GB / 2) / MB_PER_GB)) },
            )
            val hz = profile.refreshRatesHz.firstOrNull()?.roundToInt() ?: 0
            SettingsRow(
                stringResource(R.string.device_screen),
                showDivider = true,
                trailing = { Value(stringResource(R.string.device_screen_value, profile.screenWidthPx, profile.screenHeightPx, hz)) },
            )
            val advice =
                if (profile.benchmark == null) {
                    stringResource(R.string.device_not_measured)
                } else {
                    stringResource(profile.ceiling.label())
                }
            SettingsRow(stringResource(R.string.device_ceiling), showDivider = true, trailing = { Value(advice) })
            if (profile.benchmark != null) {
                SettingsRow(stringResource(R.string.device_report), showDivider = true, onClick = onShowReport)
            }
        }
        SettingsRow(
            stringResource(if (profile?.benchmark == null) R.string.device_measure else R.string.device_measure_again),
            summary = stringResource(R.string.device_measure_summary),
            showDivider = profile != null,
            onClick = onMeasure,
        )
    }
}

private const val MB_PER_GB = 1024L

@Composable
private fun Value(text: String) {
    BasicText(text, style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 17.sp))
}

private fun LevelChoice.label() =
    when (this) {
        LevelChoice.Automatic -> R.string.graphics_level_auto
        is LevelChoice.Fixed -> level.label()
    }

private fun GraphicsLevel.explanation() =
    when (this) {
        GraphicsLevel.MAXIMUM -> R.string.graphics_explain_maximum
        GraphicsLevel.BALANCED -> R.string.graphics_explain_balanced
        GraphicsLevel.PERFORMANCE -> R.string.graphics_explain_performance
    }

internal fun GraphicsLevel.label() =
    when (this) {
        GraphicsLevel.MAXIMUM -> R.string.graphics_level_maximum
        GraphicsLevel.BALANCED -> R.string.graphics_level_balanced
        GraphicsLevel.PERFORMANCE -> R.string.graphics_level_performance
    }
