package it.manlio.nova

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.feature.home.BlurLevel
import it.manlio.nova.feature.home.ClockStyle
import it.manlio.nova.feature.home.HomeFont
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
    SettingsScaffold(title = stringResource(R.string.customize_title), subtitle = stringResource(R.string.customize_subtitle)) { backdrop ->
        ThemesSection(backdrop, prefs, actions)
        WallpaperSection(backdrop, prefs, actions)
        ColorsSection(backdrop, prefs, actions.onChange)
        TextSection(backdrop, prefs, actions.onChange)
        IconsSection(backdrop, prefs, actions)
        LayoutSection(backdrop, prefs, actions.onChange)
        HiddenAndRenamed(backdrop, prefs, actions.onChange)
        SettingsSection(backdrop) { SettingsRow(stringResource(R.string.customize_done), onClick = actions.onDone) }
    }
}

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
            val active = prefs.look() == preset.prefs.look()
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
            stringResource(it.label())
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
            stringResource(it.label())
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
        Choice(R.string.customize_side, listOf(Side.LEFT, Side.RIGHT), prefs.alphabetSide, { stringResource(labelOf(it)) }) { v ->
            onChange { it.copy(alphabetSide = v) }
        }
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
