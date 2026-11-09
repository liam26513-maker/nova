@file:Suppress("MagicNumber") // i colori dello switcher, scritti dove si usano

package it.manlio.nova

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val VEIL = 0.3f
private const val CARD_HEIGHT = 0.62f
private const val CLOSE_AT = 0.28f
private const val APPEAR_MS = 180
private const val FLY_MS = 160
private val CARD_SHAPE = RoundedCornerShape(20.dp)
private val BLUR = 40.dp
private val PLACEHOLDER = Color(0xFF2A2E35)

/**
 * Le App recenti in stile iPad: lo sfondo di NOVA sfocato, le schede grandi in fila con icona e nome sopra, la più recente per
 * prima. Tocco su una scheda: la apre. Scheda trascinata verso l'alto: la chiude. Tocco fuori: alla Home.
 */
@Composable
internal fun SwitcherScreen(
    state: SwitcherState,
    backdrop: ImageBitmap?,
    onOpen: (RecentCard) -> Unit,
    onClose: (RecentCard) -> Unit,
    onCloseAll: () -> Unit,
    onHome: () -> Unit,
) {
    val homeLabel = stringResource(R.string.recents_home)
    // Invisibile finché le schede non sono pronte (sotto restano le recenti di Android), poi una sola dissolvenza: niente sfondo
    // vuoto prima e schede che spuntano dopo.
    val appear = remember { Animatable(0f) }
    val ready = state.cards != null
    LaunchedEffect(ready) { if (ready) appear.animateTo(1f, tween(APPEAR_MS)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color(0xFF0E1116))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = homeLabel, onClick = onHome),
    ) {
        backdrop?.let {
            // Sfocato dalla scheda video: morbido, senza quadretti.
            Image(it, null, Modifier.fillMaxSize().blur(BLUR), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = VEIL)))
        val cards = state.cards
        when {
            cards == null -> Unit
            cards.isEmpty() ->
                BasicText(
                    stringResource(R.string.recents_empty),
                    Modifier.align(Alignment.Center),
                    style = TextStyle(Color.White.copy(alpha = 0.85f), 22.sp, FontWeight.Medium),
                )
            else -> Cards(cards, state, onOpen, onClose)
        }
        if ((cards?.size ?: 0) > 1) {
            BasicText(
                stringResource(R.string.recents_close_all),
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onCloseAll)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                style = TextStyle(Color.White, 15.sp, FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun Cards(
    cards: List<RecentCard>,
    state: SwitcherState,
    onOpen: (RecentCard) -> Unit,
    onClose: (RecentCard) -> Unit,
) {
    val config = LocalConfiguration.current
    val ratio = config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val height = maxHeight * CARD_HEIGHT
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 56.dp),
            // Poche schede stanno al centro, come sull'iPad; tante scorrono dal bordo.
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(cards, key = { it.id }) { card ->
                Card(card, state.shots[card.id], height, height * ratio, onOpen, onClose)
            }
        }
    }
}

@Composable
private fun Card(
    card: RecentCard,
    shot: ImageBitmap?,
    height: Dp,
    width: Dp,
    onOpen: (RecentCard) -> Unit,
    onClose: (RecentCard) -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val openLabel = stringResource(R.string.recents_open, card.label)
    val closeLabel = stringResource(R.string.recents_close, card.label)
    Column(Modifier.width(width).swipeUpToClose(card.id, offset, heightPx) { onClose(card) }) {
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            card.icon?.let { Image(it, null, Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))) }
            Spacer(Modifier.width(8.dp))
            BasicText(
                card.label,
                style = TextStyle(Color.White, 15.sp, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(width, height)
                .shadow(18.dp, CARD_SHAPE)
                .clip(CARD_SHAPE)
                .background(PLACEHOLDER)
                .semantics {
                    contentDescription = openLabel
                    customActions = listOf(CustomAccessibilityAction(closeLabel) { onClose(card).let { true } })
                }.clickable { onOpen(card) },
            contentAlignment = Alignment.Center,
        ) {
            if (shot != null) {
                Image(shot, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                card.icon?.let { Image(it, null, Modifier.size(72.dp)) }
            }
        }
    }
}

/** Trascinata verso l'alto oltre [CLOSE_AT] dell'altezza la scheda vola via e si chiude; altrimenti torna al suo posto. */
@Composable
private fun Modifier.swipeUpToClose(
    key: Int,
    offset: Animatable<Float, *>,
    heightPx: Float,
    onClose: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    return graphicsLayer {
        translationY = offset.value
        alpha = 1f - (-offset.value / heightPx).coerceIn(0f, 1f)
    }.pointerInput(key) {
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    if (-offset.value > heightPx * CLOSE_AT) {
                        offset.animateTo(-heightPx * 1.2f, tween(FLY_MS))
                        onClose()
                    } else {
                        offset.animateTo(0f, spring())
                    }
                }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
        ) { change, dy ->
            change.consume()
            scope.launch { offset.snapTo((offset.value + dy).coerceAtMost(0f)) }
        }
    }
}
