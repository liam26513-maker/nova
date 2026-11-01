package it.manlio.nova

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.design.SidebarEntry
import it.manlio.nova.core.design.SidebarGlyph
import it.manlio.nova.core.design.SplitSettings
import it.manlio.nova.feature.home.BlurLevel
import it.manlio.nova.feature.home.ClockStyle
import it.manlio.nova.feature.home.HomeFont
import it.manlio.nova.feature.home.HomeLayoutStyle
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.IconShape
import it.manlio.nova.feature.home.IconSize
import it.manlio.nova.feature.home.IconStyle
import it.manlio.nova.feature.home.NovaWallpapers
import it.manlio.nova.feature.home.ScrimStrength
import it.manlio.nova.feature.home.Side
import it.manlio.nova.feature.home.TextSize
import it.manlio.nova.feature.home.TextWeight
import it.manlio.nova.feature.home.ThemeMode
import it.manlio.nova.feature.home.ThemePresets
import it.manlio.nova.feature.home.WallpaperSource

/**
 * Personalizzazione della Home sul modello di Niagara (temi pronti e condivisibili, sfondo, colori,
 * testo, orologio, icone e pacchetti di icone, disposizione e gesti, app nascoste), con grafica e
 * nomi propri. Ogni scelta si applica subito.
 */
@Composable
fun HomeCustomizeScreen(
    prefs: HomePrefs,
    actions: CustomizeActions,
) {
    BackHandler(onBack = actions.onDone)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    SplitSettings(
        title = stringResource(R.string.customize_title),
        subtitle = stringResource(R.string.customize_subtitle),
        entries = customizeEntries(),
        selectedId = selected,
        onSelect = { selected = it },
        backLabel = stringResource(R.string.customize_done),
        onBack = actions.onDone,
    ) { entry, backdrop ->
        when (entry.id) {
            CUSTOMIZE_THEMES -> ThemesSection(backdrop, prefs, actions)
            CUSTOMIZE_BACKGROUND -> WallpaperSection(backdrop, prefs, actions)
            CUSTOMIZE_COLORS -> ColorsSection(backdrop, prefs, actions.onChange)
            CUSTOMIZE_TEXT -> TextSection(backdrop, prefs, actions.onChange)
            CUSTOMIZE_ICONS -> IconsSection(backdrop, prefs, actions)
            CUSTOMIZE_LAYOUT -> LayoutSection(backdrop, prefs, actions.onChange)
            else -> HiddenAndRenamed(backdrop, prefs, actions.onChange)
        }
    }
}

@Suppress("MagicNumber") // tavolozza delle pastiglie
@Composable
private fun customizeEntries(): List<SidebarEntry> {
    val themes = stringResource(R.string.customize_themes)
    val background = stringResource(R.string.customize_background)
    val colors = stringResource(R.string.customize_colors)
    val text = stringResource(R.string.customize_text_and_clock)
    val icons = stringResource(R.string.customize_icons_section)
    val layout = stringResource(R.string.customize_layout)
    val hidden = stringResource(R.string.customize_hidden)
    return remember(themes, background, colors, text, icons, layout, hidden) {
        listOf(
            SidebarEntry(CUSTOMIZE_THEMES, themes, SidebarGlyph.LAYERS, Color(0xFF7B68D8)),
            SidebarEntry(CUSTOMIZE_BACKGROUND, background, SidebarGlyph.IMAGE, Color(0xFF2FA39A)),
            SidebarEntry(CUSTOMIZE_COLORS, colors, SidebarGlyph.DROP, Color(0xFFD6537A)),
            SidebarEntry(CUSTOMIZE_TEXT, text, SidebarGlyph.TEXT, Color(0xFF3B8BD4)),
            SidebarEntry(CUSTOMIZE_ICONS, icons, SidebarGlyph.GRID, Color(0xFFE0922F)),
            SidebarEntry(CUSTOMIZE_LAYOUT, layout, SidebarGlyph.SLIDERS, Color(0xFF6B7684)),
            SidebarEntry(CUSTOMIZE_HIDDEN, hidden, SidebarGlyph.EYE, Color(0xFF3FA34D)),
        )
    }
}

private const val CUSTOMIZE_THEMES = "themes"
private const val CUSTOMIZE_BACKGROUND = "background"
private const val CUSTOMIZE_COLORS = "colors"
private const val CUSTOMIZE_TEXT = "text"
private const val CUSTOMIZE_ICONS = "icons"
private const val CUSTOMIZE_LAYOUT = "layout"
private const val CUSTOMIZE_HIDDEN = "hidden"

@Composable
private fun ThemesSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    actions: CustomizeActions,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_themes),
        footer = stringResource(R.string.customize_themes_footer),
    ) {
        ThemePresets.all.forEachIndexed { i, preset ->
            val active = prefs.appearance() == preset.prefs.appearance()
            SettingsRow(
                preset.name,
                summary = preset.description,
                showDivider = i > 0,
                onClick = { actions.onChange { it.withLookOf(preset.prefs) } },
                trailing = if (active) ({ Check() }) else null,
            )
        }
        SettingsRow(stringResource(R.string.customize_theme_export), showDivider = true, onClick = actions.onExportTheme)
        SettingsRow(stringResource(R.string.customize_theme_import), showDivider = true, onClick = actions.onImportTheme)
    }
}

@Composable
private fun WallpaperSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    actions: CustomizeActions,
) {
    val onChange = actions.onChange
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_background),
        footer = stringResource(R.string.customize_background_footer),
    ) {
        Choice(R.string.customize_wallpaper, WallpaperSource.entries, prefs.wallpaper, { stringResource(labelOf(it)) }) { v ->
            onChange { it.copy(wallpaper = v) }
        }
        when (prefs.wallpaper) {
            WallpaperSource.SYSTEM ->
                if (!actions.canReadWallpaper) {
                    SettingsRow(
                        stringResource(R.string.customize_wallpaper_access),
                        summary = stringResource(R.string.customize_wallpaper_access_summary),
                        showDivider = true,
                        onClick = actions.onGrantWallpaperAccess,
                    )
                }
            WallpaperSource.NOVA ->
                Swatches(
                    R.string.customize_nova_wallpaper,
                    NovaWallpapers.all.map { SwatchItem(it.name, gradient = it) },
                    selected = prefs.novaWallpaper,
                ) { i -> onChange { it.copy(novaWallpaper = i) } }
            WallpaperSource.PHOTO ->
                SettingsRow(
                    stringResource(R.string.customize_pick_photo),
                    showDivider = true,
                    onClick = actions.onPickPhoto,
                )
            WallpaperSource.SOLID ->
                Swatches(
                    R.string.customize_solid_color,
                    SOLID_COLORS.map { SwatchItem(colorName(it), color = Color(it)) },
                    selected = SOLID_COLORS.indexOf(prefs.solidColor),
                ) { i -> onChange { it.copy(solidColor = SOLID_COLORS[i]) } }
        }
        if (prefs.wallpaper == WallpaperSource.SYSTEM || prefs.wallpaper == WallpaperSource.PHOTO) {
            Choice(R.string.customize_blur, BlurLevel.entries, prefs.blurLevel, { stringResource(labelOf(it)) }, divider = true) { v ->
                onChange { it.copy(blurLevel = v) }
            }
        }
        Choice(R.string.customize_scrim, ScrimStrength.entries, prefs.scrim, { stringResource(labelOf(it)) }, divider = true) { v ->
            onChange { it.copy(scrim = v) }
        }
    }
}

@Composable
private fun ColorsSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
) {
    SettingsSection(backdrop, header = stringResource(R.string.customize_colors)) {
        Choice(R.string.customize_theme_mode, ThemeMode.entries, prefs.theme, {
            stringResource(labelOf(it))
        }) { v -> onChange { it.copy(theme = v) } }
        val auto = stringResource(R.string.customize_accent_auto)
        Swatches(
            R.string.customize_accent,
            listOf(SwatchItem(auto, auto = true)) + ACCENTS.map { SwatchItem(colorName(it), color = Color(it)) },
            selected = prefs.accent?.let { ACCENTS.indexOf(it) + 1 } ?: 0,
        ) { i -> onChange { it.copy(accent = if (i == 0) null else ACCENTS[i - 1]) } }
    }
}

@Composable
private fun TextSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
) {
    SettingsSection(backdrop, header = stringResource(R.string.customize_text_and_clock)) {
        Choice(R.string.customize_text, TextSize.entries, prefs.textSize, {
            stringResource(labelOf(it))
        }) { v -> onChange { it.copy(textSize = v) } }
        Choice(R.string.customize_font, HomeFont.entries, prefs.font, { stringResource(labelOf(it)) }, divider = true) { v ->
            onChange { it.copy(font = v) }
        }
        Choice(R.string.customize_weight, TextWeight.entries, prefs.weight, { stringResource(labelOf(it)) }, divider = true) { v ->
            onChange { it.copy(weight = v) }
        }
        Choice(R.string.customize_clock, listOf(true, false), prefs.showClock, { showHide(it) }, divider = true) { v ->
            onChange { it.copy(showClock = v) }
        }
        if (prefs.showClock) {
            Choice(
                R.string.customize_clock_style,
                ClockStyle.entries,
                prefs.clockStyle,
                { stringResource(labelOf(it)) },
                divider = true,
            ) { v ->
                onChange { it.copy(clockStyle = v) }
            }
            Choice(R.string.customize_date, listOf(true, false), prefs.showDate, { showHide(it) }, divider = true) { v ->
                onChange { it.copy(showDate = v) }
            }
        }
    }
}

@Composable
private fun IconsSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    actions: CustomizeActions,
) {
    val onChange = actions.onChange
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_icons_section),
        footer = stringResource(R.string.customize_icons_footer),
    ) {
        Choice(
            R.string.customize_icons,
            listOf(true, false),
            prefs.showIcons,
            { showHide(it) },
        ) { v -> onChange { it.copy(showIcons = v) } }
        if (prefs.showIcons) {
            Choice(R.string.customize_icon_size, IconSize.entries, prefs.iconSize, { stringResource(labelOf(it)) }, divider = true) { v ->
                onChange { it.copy(iconSize = v) }
            }
            Choice(
                R.string.customize_icon_shape,
                IconShape.entries,
                prefs.iconShape,
                { stringResource(labelOf(it)) },
                divider = true,
            ) { v ->
                onChange { it.copy(iconShape = v) }
            }
            Choice(
                R.string.customize_icon_style,
                IconStyle.entries,
                prefs.iconStyle,
                { stringResource(labelOf(it)) },
                divider = true,
            ) { v ->
                onChange { it.copy(iconStyle = v) }
            }
            IconPackRows(prefs, actions)
        }
    }
}

@Composable
private fun IconPackRows(
    prefs: HomePrefs,
    actions: CustomizeActions,
) {
    SettingsRow(
        stringResource(R.string.customize_icon_pack_none),
        summary = if (actions.iconPacks.isEmpty()) stringResource(R.string.customize_icon_pack_empty) else null,
        showDivider = true,
        onClick = { actions.onChange { it.copy(iconPack = null) } },
        trailing = if (prefs.iconPack == null) ({ Check() }) else null,
    )
    actions.iconPacks.forEach { pack ->
        SettingsRow(
            pack.label,
            showDivider = true,
            onClick = { actions.onChange { it.copy(iconPack = pack.packageName) } },
            trailing = if (prefs.iconPack == pack.packageName) ({ Check() }) else null,
        )
    }
}

@Composable
private fun LayoutSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_layout),
        footer = stringResource(R.string.customize_layout_footer),
    ) {
        Choice(R.string.customize_home_layout, HomeLayoutStyle.entries, prefs.layout, { stringResource(labelOf(it)) }) { v ->
            onChange { it.copy(layout = v) }
        }
        Choice(
            R.string.customize_side,
            listOf(Side.LEFT, Side.RIGHT),
            prefs.alphabetSide,
            { stringResource(labelOf(it)) },
            divider = true,
        ) { v -> onChange { it.copy(alphabetSide = v) } }
        Choice(R.string.customize_status_bar, listOf(true, false), prefs.showStatusBar, { showHide(it) }, divider = true) { v ->
            onChange { it.copy(showStatusBar = v) }
        }
        Choice(R.string.customize_swipe_down, listOf(true, false), prefs.swipeDownNotifications, { onOff(it) }, divider = true) { v ->
            onChange { it.copy(swipeDownNotifications = v) }
        }
        Choice(R.string.customize_double_tap, listOf(true, false), prefs.doubleTapSleep, { onOff(it) }, divider = true) { v ->
            onChange { it.copy(doubleTapSleep = v) }
        }
    }
}

@Composable
private fun HiddenAndRenamed(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_hidden),
        footer = stringResource(R.string.customize_hidden_footer),
    ) {
        if (prefs.hidden.isEmpty()) SettingsRow(stringResource(R.string.customize_hidden_none))
        prefs.hidden.entries.sortedBy { it.value.lowercase() }.forEachIndexed { i, (key, label) ->
            SettingsRow(
                label,
                summary = stringResource(R.string.customize_unhide),
                showDivider = i > 0,
                onClick = { onChange { it.copy(hidden = it.hidden - key) } },
            )
        }
        if (prefs.renamed.isNotEmpty()) {
            SettingsRow(
                stringResource(R.string.customize_reset_names, prefs.renamed.size),
                showDivider = true,
                onClick = { onChange { it.copy(renamed = emptyMap()) } },
            )
        }
    }
}
