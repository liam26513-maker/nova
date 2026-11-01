package it.manlio.nova.core.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Impostazioni a due colonne come sull'iPad: a sinistra la barra laterale con le categorie, a destra il
 * dettaglio della categoria scelta. Su schermi stretti (meno di [SPLIT_MIN_WIDTH]) diventa una lista di
 * categorie che si apre sul dettaglio, con «‹» e Indietro per tornare.
 *
 * [selectedId] sta a chi chiama (di solito `rememberSaveable`): null = nessuna scelta (nella lista stretta
 * si vede l'elenco; nella vista larga vale la prima categoria). [onBack] aggiunge in cima un'uscita
 * dall'intera schermata, chiamata [backLabel].
 */
@Composable
fun SplitSettings(
    title: String,
    entries: List<SidebarEntry>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    subtitle: String? = null,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    detail: @Composable ColumnScope.(entry: SidebarEntry, backdrop: ImageBitmap) -> Unit,
) {
    val t = Nova.tokens
    val backdrop = rememberCalmBackdrop()
    val current = entries.firstOrNull { it.id == selectedId }
    BoxWithConstraints(Modifier.fillMaxSize().background(t.colors.background)) {
        Image(backdrop, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        if (maxWidth >= SPLIT_MIN_WIDTH) {
            val shown = current ?: entries.first()
            Row(
                Modifier.fillMaxSize().safeDrawingPadding().padding(NovaSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(NovaSpacing.m),
            ) {
                Sidebar(backdrop, title, subtitle, entries, shown.id, onSelect, backLabel, onBack)
                AnimatedContent(
                    targetState = shown,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    transitionSpec = { fadeIn(t.motion.spring()) togetherWith fadeOut(t.motion.spring()) },
                    label = "settings-detail",
                ) { entry -> DetailPane(entry, backdrop, detail) }
            }
        } else {
            BackHandler(enabled = current != null) { onSelect(null) }
            AnimatedContent(
                targetState = current,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(t.motion.spring()) togetherWith fadeOut(t.motion.spring()) },
                label = "settings-page",
            ) { entry ->
                if (entry == null) {
                    SettingsScaffold(title, subtitle, backLabel, onBack) { bd ->
                        SettingsSection(bd) {
                            entries.forEachIndexed { i, e ->
                                SettingsRow(
                                    e.title,
                                    showDivider = i > 0,
                                    onClick = { onSelect(e.id) },
                                    leading = { GlyphBadge(e.glyph, e.color) },
                                )
                            }
                        }
                    }
                } else {
                    SettingsScaffold(entry.title, backLabel = title, onBack = { onSelect(null) }) { bd -> detail(entry, bd) }
                }
            }
        }
    }
}

/** Barra laterale: pannello traslucido con titolo grande e categorie; la scelta è evidenziata nel colore d'accento. */
@Composable
private fun Sidebar(
    backdrop: ImageBitmap,
    title: String,
    subtitle: String?,
    entries: List<SidebarEntry>,
    selectedId: String,
    onSelect: (String?) -> Unit,
    backLabel: String?,
    onBack: (() -> Unit)?,
) {
    val t = Nova.tokens
    MaterialSurface(backdrop, Modifier.width(SIDEBAR_WIDTH).fillMaxHeight(), shape = SquircleShape(t.radii.large)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NovaSpacing.s, vertical = NovaSpacing.xs),
        ) {
            if (backLabel != null && onBack != null) BackAction(backLabel, onBack, Modifier.padding(horizontal = NovaSpacing.xs))
            BasicText(
                title,
                Modifier.padding(horizontal = NovaSpacing.xs, vertical = NovaSpacing.xs),
                style = largeTitle(t.colors.onSurface),
            )
            if (subtitle != null) {
                BasicText(
                    subtitle,
                    Modifier.padding(horizontal = NovaSpacing.xs),
                    style = TextStyle(t.colors.onSurfaceMuted, 15.sp, lineHeight = 22.sp),
                )
            }
            Spacer(Modifier.height(NovaSpacing.m))
            Column(verticalArrangement = Arrangement.spacedBy(NovaSpacing.xxs)) {
                entries.forEach { entry -> SidebarRow(entry, entry.id == selectedId) { onSelect(entry.id) } }
            }
        }
    }
}

@Composable
private fun SidebarRow(
    entry: SidebarEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = Nova.tokens
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, t.motion.spring(), label = "sidebar-press")
    val fill by animateColorAsState(if (selected) t.colors.accent else Color.Transparent, t.motion.spring(), label = "sidebar-fill")
    val ink by animateColorAsState(if (selected) Color.White else t.colors.onSurface, t.motion.spring(), label = "sidebar-ink")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .scale(scale)
            .background(fill, SquircleShape(t.radii.medium))
            .semantics { this.selected = selected }
            .clickable(interaction, indication = null, role = Role.Tab) {
                if (!selected) view.perform(NovaHaptic.SELECTION)
                onClick()
            }.padding(horizontal = NovaSpacing.xs, vertical = NovaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphBadge(entry.glyph, entry.color)
        Spacer(Modifier.width(NovaSpacing.s))
        BasicText(entry.title, style = TextStyle(ink, 17.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal))
    }
}

/** Dettaglio della categoria: titolo e gruppi, in una colonna leggibile e scorrevole. */
@Composable
private fun DetailPane(
    entry: SidebarEntry,
    backdrop: ImageBitmap,
    detail: @Composable ColumnScope.(SidebarEntry, ImageBitmap) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NovaSpacing.s, vertical = NovaSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = DETAIL_WIDTH).fillMaxWidth()) {
            BasicText(entry.title, Modifier.padding(bottom = NovaSpacing.l), style = largeTitle(Nova.tokens.colors.onSurface))
            Column(verticalArrangement = Arrangement.spacedBy(NovaSpacing.xl)) { detail(entry, backdrop) }
            Spacer(Modifier.height(NovaSpacing.xxl))
        }
    }
}

/** Da questa larghezza le Impostazioni si dividono in due colonne (il tablet in orizzontale ci arriva con ampio margine). */
val SPLIT_MIN_WIDTH = 840.dp
private val SIDEBAR_WIDTH = 340.dp
private val DETAIL_WIDTH = 720.dp
private val ROW_MIN_HEIGHT = 48.dp
