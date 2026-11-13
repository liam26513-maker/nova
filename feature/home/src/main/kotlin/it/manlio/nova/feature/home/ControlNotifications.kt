@file:Suppress("MagicNumber") // misure della scheda: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/**
 * Le notifiche accanto al centro di controllo (richiesta del 13 novembre 2026): scendono insieme alla tendina, una per una dalla
 * più recente. Toccarne una la apre; «Rispondi» (se l'app lo permette) chiede il testo; la × la chiude. Senza l'accesso alle
 * notifiche la scheda non c'è.
 */
@Composable
internal fun ControlNotifications(
    source: NotificationSource?,
    sheet: ControlSheetState,
    card: Color,
    flat: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = remember(source) { source?.isEnabled() == true }
    if (source == null || !enabled) return
    val items by source.all.collectAsState()
    val look = LocalHomeLook.current
    val inset = with(LocalDensity.current) { 48.dp.toPx() }
    Column(
        modifier
            .graphicsLayer {
                translationY = -(1f - sheet.progress) * (size.height + inset)
                alpha = (sheet.progress * 3f).coerceAtMost(1f)
            }.background(card, SquircleShape(RADIUS))
            .glass(SquircleShape(RADIUS), look.dark, flat)
            // I tocchi sulla scheda non arrivano al velo dietro, che chiuderebbe la tendina.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            stringResource(R.string.control_notifications),
            style = TextStyle(look.ink, 17.sp, FontWeight.SemiBold, fontFamily = look.family),
        )
        if (items.isEmpty()) {
            BasicText(
                stringResource(R.string.control_notifications_none),
                style = TextStyle(look.muted, 15.sp, fontFamily = look.family),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = MAX_HEIGHT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.key }) { p ->
                    NotificationRow(p, source, onClose)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    p: NotificationPreview,
    source: NotificationSource,
    onClose: () -> Unit,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val pm = LocalContext.current.packageManager
    val app =
        remember(p.pkg) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p.pkg, 0)).toString() }.getOrDefault(p.pkg)
        }
    Column(
        Modifier
            .fillMaxWidth()
            .background(look.ink.copy(alpha = 0.06f), SquircleShape(18.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                source.open(p.key)
                onClose()
            }.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                app,
                Modifier.weight(1f),
                style = TextStyle(look.muted, 12.sp, FontWeight.Medium, fontFamily = look.family),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SmallAction("✕", stringResource(R.string.control_notification_dismiss)) { source.dismiss(p.key) }
        }
        if (p.title.isNotBlank()) {
            BasicText(
                p.title,
                style = TextStyle(look.ink, 15.sp, FontWeight.SemiBold, fontFamily = look.family),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (p.text.isNotBlank()) {
            BasicText(
                p.text,
                Modifier.padding(end = 8.dp),
                style = TextStyle(look.ink, 14.sp, fontFamily = look.family),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (p.canReply) {
            val label = stringResource(R.string.control_notification_reply)
            SmallAction(label, label, Modifier.padding(top = 2.dp)) {
                source.askReply(p)
                onClose()
            }
        }
    }
}

@Composable
private fun SmallAction(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val look = LocalHomeLook.current
    BasicText(
        text,
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = description) {
                onClick()
            }.padding(horizontal = 8.dp, vertical = 6.dp),
        style = TextStyle(look.accent.copy(alpha = 1f), 14.sp, FontWeight.SemiBold, fontFamily = look.family),
    )
}

private val RADIUS = 34.dp
private val MAX_HEIGHT = 620.dp
